// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.proxy;

import org.jetbrains.annotations.Nls;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;

public abstract class NonStaticAuthenticator {
  private String requestingHost;
  private InetAddress requestingSite;
  private int requestingPort;
  private String requestingProtocol;
  private @Nls String requestingPrompt;
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

  @Nls
  protected String getRequestingPrompt() {
    return requestingPrompt;
  }

  protected void setRequestingPrompt(@Nls String requestingPrompt) {
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
