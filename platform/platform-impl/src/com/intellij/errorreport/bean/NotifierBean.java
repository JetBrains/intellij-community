/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
