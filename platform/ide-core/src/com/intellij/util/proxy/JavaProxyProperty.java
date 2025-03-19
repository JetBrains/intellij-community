// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.proxy;

/**
 * http://docs.oracle.com/javase/6/docs/technotes/guides/net/proxies.html
 * http://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html
 */
public interface JavaProxyProperty {
  String PROXY_SET = "proxySet";

  String HTTP_HOST = "http.proxyHost";
  String HTTP_PORT = "http.proxyPort";
  /** note that this property is not supported by JDK itself, but is used quite broadly in libraries */
  String HTTP_PROXY_USER = "http.proxyUser";
  /** note that this property is not supported by JDK itself, but is used quite broadly in libraries */
  String HTTP_PROXY_PASSWORD = "http.proxyPassword";
  String HTTP_NON_PROXY_HOSTS = "http.nonProxyHosts";

  String HTTPS_HOST = "https.proxyHost";
  String HTTPS_PORT = "https.proxyPort";
  /** note that this property is not supported by JDK itself, but is used quite broadly in libraries */
  String HTTPS_PROXY_USER = "https.proxyUser";
  /** note that this property is not supported by JDK itself, but is used quite broadly in libraries */
  String HTTPS_PROXY_PASSWORD = "https.proxyPassword";

  String SOCKS_HOST = "socksProxyHost";
  String SOCKS_PORT = "socksProxyPort";
  String SOCKS_VERSION = "socksProxyVersion";
  String SOCKS_USERNAME = "java.net.socks.username";
  String SOCKS_PASSWORD = "java.net.socks.password";

  String USE_SYSTEM_PROXY = "java.net.useSystemProxies";

  /**
   * @deprecated it is likely that {@link JavaProxyProperty#HTTP_PROXY_USER}/{@link JavaProxyProperty#HTTPS_PROXY_USER} should be used instead
   */
  @Deprecated
  String HTTP_USERNAME = "proxy.authentication.username";
  /**
   * @deprecated it is likely that {@link JavaProxyProperty#HTTP_PROXY_PASSWORD}/{@link JavaProxyProperty#HTTPS_PROXY_PASSWORD} should be used instead
   */
  @Deprecated
  String HTTP_PASSWORD = "proxy.authentication.password";
}
