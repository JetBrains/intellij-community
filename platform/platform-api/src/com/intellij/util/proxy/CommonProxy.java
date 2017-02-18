/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CommonProxy extends ProxySelector {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.proxy.CommonProxy");

  private final static CommonProxy ourInstance = new CommonProxy();
  private final CommonAuthenticator myAuthenticator = new CommonAuthenticator();

  private final static ThreadLocal<Boolean> ourReenterDefence = new ThreadLocal<>();

  public final static List<Proxy> NO_PROXY_LIST = Collections.singletonList(Proxy.NO_PROXY);
  private final static long ourErrorInterval = TimeUnit.MINUTES.toMillis(3);
  private static volatile int ourNotificationCount;
  private volatile static long ourErrorTime;
  private volatile static ProxySelector ourWrong;
  private static final AtomicReference<Map<String, String>> ourProps = new AtomicReference<>();

  static {
    ProxySelector.setDefault(ourInstance);
  }

  private final Object myLock = new Object();
  private final Set<Pair<HostInfo, Thread>> myNoProxy = new THashSet<>();

  private final Map<String, ProxySelector> myCustom = new THashMap<>();
  private final Map<String, NonStaticAuthenticator> myCustomAuth = new THashMap<>();

  public static CommonProxy getInstance() {
    return ourInstance;
  }

  private CommonProxy() {
    ensureAuthenticator();
  }

  public static void isInstalledAssertion() {
    final ProxySelector aDefault = ProxySelector.getDefault();
    if (ourInstance != aDefault) {
      // to report only once
      if (ourWrong != aDefault || itsTime()) {
        LOG.error("ProxySelector.setDefault() was changed to [" + aDefault.toString() + "] - other than com.intellij.util.proxy.CommonProxy.ourInstance.\n" +
                  "This will make some " + ApplicationNamesInfo.getInstance().getProductName() + " network calls fail.\n" +
                  "Instead, methods of com.intellij.util.proxy.CommonProxy should be used for proxying.");
        ourWrong = aDefault;
      }
      ProxySelector.setDefault(ourInstance);
      ourInstance.ensureAuthenticator();
    }
    assertSystemPropertiesSet();
  }

  private static boolean itsTime() {
    final boolean b = System.currentTimeMillis() - ourErrorTime > ourErrorInterval && ourNotificationCount < 5;
    if (b) {
      ourErrorTime = System.currentTimeMillis();
      ++ ourNotificationCount;
    }
    return b;
  }

  private static void assertSystemPropertiesSet() {
    final Map<String, String> props = getOldStyleProperties();

    final Map<String, String> was = ourProps.get();
    if (Comparing.equal(was, props) && ! itsTime()) return;
    ourProps.set(props);

    final String message = getMessageFromProps(props);
    if (message != null) {
      // we only intend to somehow report possible misconfiguration
      // will not show to the user since on Mac OS this setting is typical
      LOG.info(message);
    }
  }

  @Nullable
  public static String getMessageFromProps(Map<String, String> props) {
    String message = null;
    for (Map.Entry<String, String> entry : props.entrySet()) {
      if (! StringUtil.isEmptyOrSpaces(entry.getValue())) {
        message = CommonBundle.message("label.old.way.jvm.property.used", entry.getKey(), entry.getValue());
        break;
      }
    }
    return message;
  }

  public static Map<String, String> getOldStyleProperties() {
    final Map<String, String> props = new HashMap<>();
    props.put(JavaProxyProperty.HTTP_HOST, System.getProperty(JavaProxyProperty.HTTP_HOST));
    props.put(JavaProxyProperty.HTTPS_HOST, System.getProperty(JavaProxyProperty.HTTPS_HOST));
    props.put(JavaProxyProperty.SOCKS_HOST, System.getProperty(JavaProxyProperty.SOCKS_HOST));
    return props;
  }

  public void ensureAuthenticator() {
    Authenticator.setDefault(myAuthenticator);
  }

  public void noProxy(@NotNull final String protocol, @NotNull final String host, final int port) {
    synchronized (myLock) {
      LOG.debug("no proxy added: " + protocol + "://" + host + ":" + port);
      myNoProxy.add(Pair.create(new HostInfo(protocol, host, port), Thread.currentThread()));
    }
  }

  public void removeNoProxy(@NotNull final String protocol, @NotNull final String host, final int port) {
    synchronized (myLock) {
      LOG.debug("no proxy removed: " + protocol + "://" + host + ":" + port);
      myNoProxy.remove(Pair.create(new HostInfo(protocol, host, port), Thread.currentThread()));
    }
  }

  public void noAuthentication(@NotNull final String protocol, @NotNull final String host, final int port) {
    synchronized (myLock) {
      LOG.debug("no proxy added: " + protocol + "://" + host + ":" + port);
      myNoProxy.add(Pair.create(new HostInfo(protocol, host, port), Thread.currentThread()));
    }
  }

  @SuppressWarnings("unused")
  public void removeNoAuthentication(@NotNull final String protocol, @NotNull final String host, final int port) {
    synchronized (myLock) {
      LOG.debug("no proxy removed: " + protocol + "://" + host + ":" + port);
      myNoProxy.remove(Pair.create(new HostInfo(protocol, host, port), Thread.currentThread()));
    }
  }

  public void setCustom(@NotNull final String key, @NotNull final ProxySelector proxySelector) {
    synchronized (myLock) {
      LOG.debug("custom set: " + key + ", " + proxySelector.toString());
      myCustom.put(key, proxySelector);
    }
  }

  public void setCustomAuth(@NotNull final String key, final NonStaticAuthenticator authenticator) {
    synchronized (myLock) {
      LOG.debug("custom auth set: " + key + ", " + authenticator.toString());
      myCustomAuth.put(key, authenticator);
    }
  }

  public void removeCustomAuth(@NotNull final String key) {
    synchronized (myLock) {
      LOG.debug("custom auth removed: " + key);
      myCustomAuth.remove(key);
    }
  }

  public void removeCustom(@NotNull final String key) {
    synchronized (myLock) {
      LOG.debug("custom set: " + key);
      myCustom.remove(key);
    }
  }

  public List<Proxy> select(@NotNull URL url) {
    return select(createUri(url));
  }

  @Override
  public List<Proxy> select(@Nullable URI uri) {
    isInstalledAssertion();
    if (uri == null) {
      return NO_PROXY_LIST;
    }
    LOG.debug("CommonProxy.select called for " + uri.toString());

    if (Boolean.TRUE.equals(ourReenterDefence.get())) {
      return NO_PROXY_LIST;
    }
    try {
      ourReenterDefence.set(Boolean.TRUE);
      String host = StringUtil.notNullize(uri.getHost());
      if (NetUtils.isLocalhost(host)) {
        return NO_PROXY_LIST;
      }

      final HostInfo info = new HostInfo(uri.getScheme(), host, correctPortByProtocol(uri));
      final Map<String, ProxySelector> copy;
      synchronized (myLock) {
        if (myNoProxy.contains(Pair.create(info, Thread.currentThread()))) {
          LOG.debug("CommonProxy.select returns no proxy (in no proxy list) for " + uri.toString());
          return NO_PROXY_LIST;
        }
        copy = new THashMap<>(myCustom);
      }
      for (Map.Entry<String, ProxySelector> entry : copy.entrySet()) {
        final List<Proxy> proxies = entry.getValue().select(uri);
        if (!ContainerUtil.isEmpty(proxies)) {
          LOG.debug("CommonProxy.select returns custom proxy for " + uri.toString() + ", " + proxies.toString());
          return proxies;
        }
      }
      return NO_PROXY_LIST;
    }
    finally {
      ourReenterDefence.remove();
    }
  }

  private static int correctPortByProtocol(@NotNull URI uri) {
    if (uri.getPort() == -1) {
      if ("http".equals(uri.getScheme())) {
        return ProtocolDefaultPorts.HTTP;
      }
      else if ("https".equals(uri.getScheme())) {
        return ProtocolDefaultPorts.SSL;
      }
    }
    return uri.getPort();
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    LOG.info("connect failed to " + uri.toString() + ", sa: " + sa.toString(), ioe);

    final Map<String, ProxySelector> copy;
    synchronized (myLock) {
      copy = new THashMap<>(myCustom);
    }
    for (Map.Entry<String, ProxySelector> entry : copy.entrySet()) {
      entry.getValue().connectFailed(uri, sa, ioe);
    }
  }

  private class CommonAuthenticator extends Authenticator {
    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      final String siteStr = getRequestingSite() == null ? null : getRequestingSite().toString();
      LOG.debug("CommonAuthenticator.getPasswordAuthentication called for " + siteStr);
      final String host = getHostNameReliably(getRequestingHost(), getRequestingSite(), getRequestingURL());
      final int port = getRequestingPort();

      final Map<String, NonStaticAuthenticator> copy;
      synchronized (myLock) {
        // for hosts defined as no proxy we will NOT pass authentication to not provoke credentials
        final HostInfo hostInfo = new HostInfo(getRequestingProtocol(), host, port);
        final Pair<HostInfo, Thread> pair = Pair.create(hostInfo, Thread.currentThread());
        if (myNoProxy.contains(pair)) {
          LOG.debug("CommonAuthenticator.getPasswordAuthentication found host in no proxies set (" + siteStr + ")");
          return null;
        }
        copy = new HashMap<>(myCustomAuth);
      }

      if (! copy.isEmpty()) {
        for (Map.Entry<String, NonStaticAuthenticator> entry : copy.entrySet()) {
          final NonStaticAuthenticator authenticator = entry.getValue();
          prepareAuthenticator(authenticator);
          final PasswordAuthentication authentication = authenticator.getPasswordAuthentication();
          if (authentication != null) {
            LOG.debug("CommonAuthenticator.getPasswordAuthentication found custom authenticator for " + siteStr + ", " + entry.getKey() +
              ", " + authenticator.toString());
            logAuthentication(authentication);
            return authentication;
          }
        }
      }
      return null;
    }

    private void prepareAuthenticator(NonStaticAuthenticator authenticator) {
      authenticator.setRequestingHost(getRequestingHost());
      authenticator.setRequestingSite(getRequestingSite());
      authenticator.setRequestingPort(getRequestingPort());
      authenticator.setRequestingProtocol(getRequestingProtocol());//http
      authenticator.setRequestingPrompt(getRequestingPrompt());
      authenticator.setRequestingScheme(getRequestingScheme());//ntlm
      authenticator.setRequestingURL(getRequestingURL());
      authenticator.setRequestorType(getRequestorType());
    }

    private void logAuthentication(PasswordAuthentication authentication) {
      if (authentication == null) {
        LOG.debug("CommonAuthenticator.getPasswordAuthentication returned null");
      } else {
        LOG.debug("CommonAuthenticator.getPasswordAuthentication returned authentication pair with login: " + authentication.getUserName());
      }
    }
  }

  public static String getHostNameReliably(final String requestingHost, final InetAddress site, final URL requestingUrl) {
    String host = requestingHost;
    if (host == null) {
      if (site != null) {
        host = site.getHostName();
      }
      else if (requestingUrl != null) {
        host = requestingUrl.getHost();
      }
    }
    host = host == null ? "" : host;
    return host;
  }

  private static URI createUri(final URL url) {
    return VfsUtil.toUri(url.toString());
  }

  public static class HostInfo {
    public final String myProtocol;
    public final String myHost;
    public final int myPort;

    public HostInfo(@Nullable String protocol, @NotNull String host, int port) {
      myPort = port;
      myHost = host;
      myProtocol = protocol;
    }

    public String getProtocol() {
      return myProtocol;
    }

    public String getHost() {
      return myHost;
    }

    public int getPort() {
      return myPort;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      HostInfo info = (HostInfo)o;
      return myPort == info.myPort && myHost.equals(info.myHost) && Comparing.equal(myProtocol, info.myProtocol);
    }

    @Override
    public int hashCode() {
      int result = myProtocol != null ? myProtocol.hashCode() : 0;
      result = 31 * result + myHost.hashCode();
      result = 31 * result + myPort;
      return result;
    }
  }




}
