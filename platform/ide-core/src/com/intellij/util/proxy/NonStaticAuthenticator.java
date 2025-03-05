// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.proxy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * @deprecated given that {@link Authenticator#requestPasswordAuthenticationInstance(String, InetAddress, int, String, String, String, URL, Authenticator.RequestorType)}
 * was introduced in java 9, this class is not needed anymore, please migrate to {@link Authenticator}
 */
@Deprecated
@SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
public abstract class NonStaticAuthenticator {
  private String requestingHost;
  private InetAddress requestingSite;
  private int requestingPort;
  private String requestingProtocol;
  private @Nls String requestingPrompt;
  private String requestingScheme;
  private URL requestingURL;
  private Authenticator.RequestorType requestingAuthType;

  private final Authenticator wrapper = new AuthenticatorWrapper();

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

  protected @Nls String getRequestingPrompt() {
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

  @ApiStatus.Internal
  public PasswordAuthentication requestPasswordAuthenticationInstance(String host,
                                                                      InetAddress addr,
                                                                      int port,
                                                                      String protocol,
                                                                      @Nls String prompt,
                                                                      String scheme,
                                                                      URL url,
                                                                      Authenticator.RequestorType reqType) {
    synchronized (this) {
      this.requestingHost = host;
      this.requestingSite = addr;
      this.requestingPort = port;
      this.requestingProtocol = protocol;
      this.requestingPrompt = prompt;
      this.requestingScheme = scheme;
      this.requestingURL = url;
      this.requestingAuthType = reqType;
      return this.getPasswordAuthentication();
    }
  }

  @ApiStatus.Internal
  public Authenticator asAuthenticator() {
    return wrapper;
  }

  private class AuthenticatorWrapper extends Authenticator {
    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      synchronized (NonStaticAuthenticator.this) {
        //noinspection HardCodedStringLiteral
        return NonStaticAuthenticator.this
          .requestPasswordAuthenticationInstance(getRequestingHost(), getRequestingSite(), getRequestingPort(), getRequestingProtocol(),
                                                 getRequestingPrompt(), getRequestingScheme(), getRequestingURL(), getRequestorType());
      }
    }
  }
}
