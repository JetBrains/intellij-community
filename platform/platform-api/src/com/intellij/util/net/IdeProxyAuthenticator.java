// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/// **If someone decides to change this implementation, keep this in mind**
/// * For _basic_ authentication scheme, the JRE caches successful proxy authentications against the authenticator instance
/// (or against null if the default authenticator is used). So for later requests cached credentials will be used by JDK automatically without
/// calling [#getPasswordAuthentication] again. Authentication cache is dropped if the proxy returns 407 and is re-requested again.
/// * For _SOCKS_, if auth is required, [#getPasswordAuthentication] is called for every connection: [java.net.SocksSocketImpl#authenticate].
/// * Authentication cache that works for _basic_ scheme does not work for _NTLM_ or _Kerberos_. The observed behavior is that
/// [#getPasswordAuthentication] is called for every connection. Probably this happens due to multistage auth.
///
/// This has the following consequences:
/// It is not possible by the means of [Authenticator] API to distinguish the reason of authentication request in general case:
/// is it because previous credentials that were valid turned invalid, and we are called second+ time for the same original request,
/// or because the underlying infrastructure just calls [#getPasswordAuthentication] for every connection?
/// In turn, it means that in general we can't really tell if we need to ask the user to provide new credentials (maybe they made a typo in password).
///
/// It does not seem possible to work around this issue robustly on the [Authenticator] level.
///
/// That said, the current implementation returns any already known credentials for every request.
/// The only help the user can get is the "Check connection" button in the proxy settings page...
///
/// @see ProxyAuthentication
@ApiStatus.Internal
public class IdeProxyAuthenticator extends Authenticator {
  private static final Logger LOG = Logger.getInstance(IdeProxyAuthenticator.class);

  private static final int LRU_SIZE = 100;

  private static final Map<String, Void> proxiedUrls = new LinkedHashMap<>(LRU_SIZE) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Void> eldest) {
      return size() > LRU_SIZE;
    }
  };

  static boolean isProxied(@NotNull URI uri) {
    synchronized (proxiedUrls) {
      return proxiedUrls.containsKey(uri.toString());
    }
  }

  private final ProxyAuthentication proxyAuth;

  public IdeProxyAuthenticator(@NotNull ProxyAuthentication proxyAuth) {
    this.proxyAuth = proxyAuth;
  }

  @Override
  protected PasswordAuthentication getPasswordAuthentication() {
    return requestPasswordAuthenticationInstance(
      getRequestingHost(), getRequestingSite(), getRequestingPort(), getRequestingProtocol(), getRequestingPrompt(),
      getRequestingScheme(), getRequestingURL(), getRequestorType()
    );
  }

  @Override
  public PasswordAuthentication requestPasswordAuthenticationInstance(
    @Nullable String host,
    @Nullable InetAddress addr,
    int port,
    @Nullable String protocol,
    @NlsSafe String prompt,
    @Nullable String scheme,
    @Nullable URL url,
    RequestorType reqType
  ) {
    if (LOG.isDebugEnabled()) LOG.debug(
      "Auth@" + Integer.toHexString(System.identityHashCode(this)) + "{type=" + reqType + ", scheme=" + scheme +
      ", protocol=" + protocol + ", prompt=" + prompt + ", host=" + host + ", port=" + port + ", site=" + addr + ", url=" + url +
      "} on " + Thread.currentThread()
    );

    // java.net.SocksSocketImpl#authenticate : there is SOCKS proxy auth, but without RequestorType passing
    var isProxy = RequestorType.PROXY == reqType || "SOCKS authentication".equals(prompt);
    if (!isProxy) return null;

    var hostName = requireNonNull(ProxyUtils.getHostNameReliably(host, addr, url), "");

    var credentials = proxyAuth.getKnownAuthentication(hostName, port);
    if (credentials == null || credentials.getUserName() == null || credentials.getPassword() == null) {
      // TODO condition for password exists because HttpConfigurable does not drop PROXY_AUTHENTICATION flag when it erases the password...
      //  Historically empty passwords were not supported anyway...
      credentials = proxyAuth.getPromptedAuthentication(prompt, hostName, port);
    }
    if (credentials != null && credentials.getUserName() != null) {
      if (url != null) proxiedUrls.put(url.toString(), null);
      var password = credentials.getPassword();
      return new PasswordAuthentication(credentials.getUserName(), password != null ? password.toCharArray() : new char[0]);
    }

    return null;
  }
}
