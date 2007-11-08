package com.intellij.errorreport.bean;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 5, 2003
 * Time: 9:36:45 PM
 * To change this template use Options | File Templates.
 */
public class NotifierBean {
    private String id;
    private String email;
    private String itnLogin;
    private String itnPassword;

    public String getItnPassword() {
        return itnPassword;
    }

    public void setItnPassword(String eapPassword) {
        itnPassword = eapPassword;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getItnLogin() {
        return itnLogin;
    }

    public void setItnLogin(String itnLogin) {
        this.itnLogin = itnLogin;
    }
}
