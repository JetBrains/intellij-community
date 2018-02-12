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

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 *
 * http://docs.oracle.com/javase/6/docs/technotes/guides/net/proxies.html
 * http://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html
 */
public interface JavaProxyProperty {
  String PROXY_SET = "proxySet";
  String HTTP_HOST = "http.proxyHost";
  String HTTP_PORT = "http.proxyPort";

  String HTTPS_HOST = "https.proxyHost";
  String HTTPS_PORT = "https.proxyPort";

  String SOCKS_HOST = "socksProxyHost";
  String SOCKS_PORT = "socksProxyPort";
  String SOCKS_VERSION = "socksProxyVersion";
  String SOCKS_USERNAME = "java.net.socks.username";
  String SOCKS_PASSWORD = "java.net.socks.password";

  String USE_SYSTEM_PROXY = "java.net.useSystemProxies";

  // ????? seems not real
  String HTTP_USERNAME = "proxy.authentication.username";
  String HTTP_PASSWORD = "proxy.authentication.password";
}
