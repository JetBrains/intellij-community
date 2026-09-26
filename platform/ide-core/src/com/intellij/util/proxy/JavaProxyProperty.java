// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.proxy;

/// [Java Networking and Proxies](http://docs.oracle.com/javase/6/docs/technotes/guides/net/proxies.html),
/// [Networking Properties](http://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html)
public interface JavaProxyProperty {
  @SuppressWarnings("unused") String PROXY_SET = "proxySet";

  String HTTP_HOST = "http.proxyHost";
  String HTTP_PORT = "http.proxyPort";
  /// Note: this property is not supported by the JRE but is used quite broadly in libraries.
  String HTTP_PROXY_USER = "http.proxyUser";
  /// Note: this property is not supported by the JRE but is used quite broadly in libraries.
  String HTTP_PROXY_PASSWORD = "http.proxyPassword";
  String HTTP_NON_PROXY_HOSTS = "http.nonProxyHosts";

  String HTTPS_HOST = "https.proxyHost";
  String HTTPS_PORT = "https.proxyPort";
  /// Note: this property is not supported by the JRE but is used quite broadly in libraries.
  String HTTPS_PROXY_USER = "https.proxyUser";
  /// Note: this property is not supported by the JRE but is used quite broadly in libraries.
  String HTTPS_PROXY_PASSWORD = "https.proxyPassword";

  String SOCKS_HOST = "socksProxyHost";
  String SOCKS_PORT = "socksProxyPort";
  @SuppressWarnings("unused") String SOCKS_VERSION = "socksProxyVersion";
  String SOCKS_USERNAME = "java.net.socks.username";
  String SOCKS_PASSWORD = "java.net.socks.password";

  String USE_SYSTEM_PROXY = "java.net.useSystemProxies";

  /// @deprecated it is likely that [#HTTP_PROXY_USER]/[#HTTPS_PROXY_USER] should be used instead
  @Deprecated(forRemoval = true)
  String HTTP_USERNAME = "proxy.authentication.username";
  /// @deprecated it is likely that [#HTTP_PROXY_PASSWORD]/[#HTTPS_PROXY_PASSWORD] should be used instead
  @Deprecated(forRemoval = true)
  String HTTP_PASSWORD = "proxy.authentication.password";
}
