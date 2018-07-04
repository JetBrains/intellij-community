// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.aether;

import org.eclipse.aether.repository.*;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.jetbrains.annotations.Nullable;

import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This is a modified copy of the corresponding Aether class that adds support for https proxy types
 */
final class JreProxySelector implements ProxySelector {

  JreProxySelector() {
  }

  public Proxy getProxy(RemoteRepository repository) {
    return getProxy(repository.getUrl());
  }

  @Nullable
  public Proxy getProxy(final String url) {
    try {
      final java.net.ProxySelector systemSelector = java.net.ProxySelector.getDefault();
      if (systemSelector == null) {
        return null;
      }
      final URI uri = new URI(url).parseServerAuthority();
      final List<java.net.Proxy> selected = systemSelector.select(uri);
      if (selected == null || selected.isEmpty()) {
        return null;
      }
      for (java.net.Proxy proxy : selected) {
        if (proxy.type() == java.net.Proxy.Type.HTTP && isValid(proxy.address())) {
          final String proxyType = chooseProxyType(uri.getScheme());
          if (proxyType != null) {
            final InetSocketAddress addr = (InetSocketAddress)proxy.address();
            return new Proxy(proxyType, addr.getHostName(), addr.getPort(), JreProxyAuthentication.INSTANCE);
          }
        }
      }
    }
    catch (Throwable e) {
      // URL invalid or not accepted by selector or no selector at all, simply use no proxy
    }
    return null;
  }

  private static String chooseProxyType(final String protocol) {
    if (Proxy.TYPE_HTTP.equals(protocol)) {
      return Proxy.TYPE_HTTP;
    }
    if (Proxy.TYPE_HTTPS.equals(protocol)) {
      return Proxy.TYPE_HTTPS;
    }
    return null;
  }

  private static boolean isValid(SocketAddress address) {
    if (address instanceof InetSocketAddress) {
      /*
       * NOTE: On some platforms with java.net.useSystemProxies=true, unconfigured proxies show up as proxy
       * objects with empty host and port 0.
       */
      final InetSocketAddress addr = (InetSocketAddress)address;
      return addr.getPort() > 0 && addr.getHostName() != null && !addr.getHostName().isEmpty();
    }
    return false;
  }

  private static final class JreProxyAuthentication implements Authentication {

    public static final Authentication INSTANCE = new JreProxyAuthentication();

    public void fill(AuthenticationContext context, String key, Map<String, String> data) {
      Proxy proxy = context.getProxy();
      if (proxy == null) {
        return;
      }
      if (!AuthenticationContext.USERNAME.equals(key) && !AuthenticationContext.PASSWORD.equals(key)) {
        return;
      }

      try {
        URL url;
        String protocol = "http";
        try {
          url = new URL(context.getRepository().getUrl());
          protocol = url.getProtocol();
        }
        catch (Exception e) {
          url = null;
        }

        PasswordAuthentication auth = Authenticator.requestPasswordAuthentication(
          proxy.getHost(), null, proxy.getPort(), protocol, "Credentials for proxy " + proxy, null, url, Authenticator.RequestorType.PROXY
        );
        if (auth != null) {
          context.put(AuthenticationContext.USERNAME, auth.getUserName());
          context.put(AuthenticationContext.PASSWORD, auth.getPassword());
        }
        else {
          context.put(AuthenticationContext.USERNAME, System.getProperty(protocol + ".proxyUser"));
          context.put(AuthenticationContext.PASSWORD, System.getProperty(protocol + ".proxyPassword"));
        }
      }
      catch (SecurityException e) {
        // oh well, let's hope the proxy can do without auth
      }
    }

    public void digest(AuthenticationDigest digest) {
      // we don't know anything about the JRE's current authenticator, assume the worst (i.e. interactive)
      digest.update(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj || (obj != null && getClass().equals(obj.getClass()));
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }
}

