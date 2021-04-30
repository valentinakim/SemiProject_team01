package member.model;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.sql.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import javax.sql.DataSource;

import util.security.AES256;
import util.security.SecretMyKey;
import util.security.Sha256;

public class MemberDAO implements InterMemberDAO {

	private DataSource ds; // DataSource ds 는 아파치톰캣이 제공하는 DBCP(DB Connection Pool) 이다.
	private Connection conn;
	private PreparedStatement pstmt;
	private ResultSet rs;
	
	private AES256 aes; // 클래스의 객체가 인스턴스이기 때문에 메소드가 필요하다.
	
	// 생성자
	public MemberDAO() {
		try {
			Context initContext = new InitialContext();
			Context envContext  = (Context)initContext.lookup("java:/comp/env");
			ds = (DataSource)envContext.lookup("jdbc/semioracle");
			
			aes = new AES256(SecretMyKey.KEY);
			// SecretMyKey.KEY은 우리가 만든 비밀키이다.
		} catch(NamingException e) {
			e.printStackTrace();
		} catch(UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	// 사용한 자원을 반납하는 close() 메소드 생성하기 
    private void close() {
       try {
          if(rs != null)    {rs.close();    rs=null;}
          if(pstmt != null) {pstmt.close(); pstmt=null;}
          if(conn != null)  {conn.close();  conn=null;}
       } catch(SQLException e) {
          e.printStackTrace();
       }
    }
	
    /////////////////////////////////////////////////////////////////////////////////////////////////
	// 아이디 중복체크   
	@Override
	public boolean idDuplicateCheck(String userid) throws SQLException {
		boolean b = false;
		
		try {
			conn = ds.getConnection();
			String sql = " select userid "
					   + " from tbl_member "
					   + " where userid= ? ";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, userid);
			
			rs = pstmt.executeQuery();
			b=rs.next();

		} finally {
			close();
		}
		
		return b;
	}// end of public boolean idDuplicateCheck(String userid) throws SQLException --------------------------------------------
	
	
	// 회원가입하기
	@Override
	public int registerMember(MemberVO member) throws SQLException {
		int n=0;
		
		try {
			conn = ds.getConnection();

			String sql = " insert into tbl_member(userid, pwd, name, email, mobile, postcode, address, detailaddress, extraaddress, gender, birthday, adagreements) "  + 
					     " values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ";
			

			pstmt = conn.prepareStatement(sql);
			
			pstmt.setString(1, member.getUserid());
			pstmt.setString(2, Sha256.encrypt(member.getPwd())); // 암호 단방향 암호화
			pstmt.setString(3, member.getName());
			pstmt.setString(4, aes.encrypt(member.getEmail())); // 이메일 양방향 암호화
			pstmt.setString(5, aes.encrypt(member.getMobile())); // 휴대폰 번호 양방향 암호화
			pstmt.setString(6, member.getPostcode());
	        pstmt.setString(7, member.getAddress());
	        pstmt.setString(8, member.getDetailaddress());
	        pstmt.setString(9, member.getExtraaddress());
	        pstmt.setString(10, member.getGender());
	        pstmt.setString(11, member.getBirthday());
	        pstmt.setString(12, member.getAdagreements());
	        
	        n = pstmt.executeUpdate();
	        
	        // 로그인 히스토리  <== 정정하기
		
		} catch(GeneralSecurityException | UnsupportedEncodingException e) {
			e.printStackTrace();
		} finally {
			close();
		}
		
		return n;
	}// end of public int registerMember(MemberVO member) throws SQLException ---------------------------------------------

	
	// 로그인하기
	@Override
	public MemberVO loginConfirm(Map<String, String> paraMap) throws SQLException {

		MemberVO loginuser = new MemberVO();
		
		try {
			conn = ds.getConnection();
			
			String sql = " select userid, name, point, fk_memberlevel, trunc((sysdate-lastpwdchangedate)/60), idle, email, mobile, address, detailaddress, extraaddress, postcode "
					   + " from tbl_member where userid=? and pwd=? ";
			
			pstmt = conn.prepareStatement(sql);			
			pstmt.setString(1, paraMap.get("userid"));
			pstmt.setString(2, Sha256.encrypt(paraMap.get("pwd"))); // 암호 단방향 암호화
			
	        rs = pstmt.executeQuery();
	        
	        if(rs.next()) {	// 아이디와 비밀번호가 있다면,
	    		
	        	loginuser.setUserid(rs.getString(1));
	        	loginuser.setName(rs.getString(2));
	        	loginuser.setPoint(rs.getInt(3));
	        	loginuser.setLevel(rs.getString(4));
	        	loginuser.setPwdCycleMonth(rs.getInt(5));
	        	loginuser.setIdle(rs.getString(6));
	        	loginuser.setEmail(rs.getString(7));
	        	loginuser.setMobile(rs.getString(8));
	        	loginuser.setAddress(rs.getString(9));
	        	loginuser.setDetailaddress(rs.getString(10));
	        	loginuser.setExtraaddress(rs.getString(11));
	        	loginuser.setPostcode(rs.getString(12));
	    		
	        	sql = " insert into tbl_loginhistory(fk_userid, clientip) "	// 로그인 기록 남기기
	        	    + " values(?,?) ";
	        	
	        	pstmt = conn.prepareStatement(sql);			
				pstmt.setString(1, paraMap.get("userid"));
				pstmt.setString(2, paraMap.get("clientip")); 
				pstmt.executeUpdate();
	        } 		
	        
		} catch(SQLException e) {
			e.printStackTrace();
		} finally {
			close();
		}
		
		return loginuser;
	}// public MemberVO registerMember(String userid, String pwd) ------------------------------------------------------------

		
}
