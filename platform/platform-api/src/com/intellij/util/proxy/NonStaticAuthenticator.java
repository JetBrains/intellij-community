/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.proxy;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/21/13
 * Time: 11:17 PM
 */
public abstract class NonStaticAuthenticator {
  private String requestingHost;
  private InetAddress requestingSite;
  private int requestingPort;
  private String requestingProtocol;
  private String requestingPrompt;
  private String requestingScheme;
  private URL requestingURL;
  private Authenticator.RequestorType requestingAuthType;

  public abstract PasswordAuthentication getPasswordAuthentication();

  protected String getRequestingHost() {
    return requestingHost;
  }

  protected void setRequestingHost(String requestingHost) {
    this.requestingHost = requestingHost;
  }

  protected InetAddress getRequestingSite() {
    return requestingSite;
  }

  protected void setRequestingSite(InetAddress requestingSite) {
    this.requestingSite = requestingSite;
  }

  protected int getRequestingPort() {
    return requestingPort;
  }

  protected void setRequestingPort(int requestingPort) {
    this.requestingPort = requestingPort;
  }

  protected String getRequestingProtocol() {
    return requestingProtocol;
  }

  protected void setRequestingProtocol(String requestingProtocol) {
    this.requestingProtocol = requestingProtocol;
  }

  protected String getRequestingPrompt() {
    return requestingPrompt;
  }

  protected void setRequestingPrompt(String requestingPrompt) {
    this.requestingPrompt = requestingPrompt;
  }

  protected String getRequestingScheme() {
    return requestingScheme;
  }

  protected void setRequestingScheme(String requestingScheme) {
    this.requestingScheme = requestingScheme;
  }

  protected URL getRequestingURL() {
    return requestingURL;
  }

  protected void setRequestingURL(URL requestingURL) {
    this.requestingURL = requestingURL;
  }

  protected Authenticator.RequestorType getRequestorType() {
    return requestingAuthType;
  }

  protected void setRequestorType(Authenticator.RequestorType requestingAuthType) {
    this.requestingAuthType = requestingAuthType;
  }
}
