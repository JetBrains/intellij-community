// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.proxy;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @deprecated use {@link com.intellij.util.net.JdkProxyProvider} for the main proxy provider of the application
 */
@SuppressWarnings("JavadocReference")
@Deprecated
public final class CommonProxy extends ProxySelector {
  static final Logger LOG = Logger.getInstance(CommonProxy.class);

  private static final CommonProxy ourInstance = new CommonProxy();

  /** @deprecated use {@link com.intellij.util.net.ProxyUtils#NO_PROXY_LIST} */
  @ApiStatus.Internal
  @Deprecated
  public static final List<Proxy> NO_PROXY_LIST = Collections.singletonList(Proxy.NO_PROXY);
  private static final long ourErrorInterval = TimeUnit.MINUTES.toMillis(3);
  private static final AtomicInteger ourNotificationCount = new AtomicInteger();
  private static volatile long ourErrorTime;
  private static volatile ProxySelector ourWrong;
  private static final AtomicReference<Map<String, String>> ourProps = new AtomicReference<>();

  private final Object myLock = new Object();

  private final Map<String, AccessToken> myCustomAuthRegistrations = new HashMap<>();

  public static CommonProxy getInstance() {
    return ourInstance;
  }

  private CommonProxy() {
    ensureAuthenticator();
  }

  /**
   * @deprecated use {@link com.intellij.util.net.JdkProxyProvider#ensureDefault()}
   */
  @Deprecated
  public static void isInstalledAssertion() {
    final ProxySelector aDefault = ProxySelector.getDefault();
    if (CommonProxyCompatibility.mainProxySelector != null && CommonProxyCompatibility.mainProxySelector != aDefault) {
      // to report only once
      if (ourWrong != aDefault || itsTime()) {
        LOG.error("ProxySelector.setDefault() was changed to [" + aDefault.toString() + "] - other than com.intellij.util.proxy.CommonProxy.myMainProxySelector.\n" +
                  "This will make some " + ApplicationNamesInfo.getInstance().getProductName() + " network calls fail.\n" +
                  "Instead, methods of com.intellij.util.net.ProxyService should be used for proxying.");
        ourWrong = aDefault;
      }
      ProxySelector.setDefault(CommonProxyCompatibility.mainProxySelector);
      ourInstance.ensureAuthenticator();
    }
    assertSystemPropertiesSet();
  }

  private static boolean itsTime() {
    final boolean b = System.currentTimeMillis() - ourErrorTime > ourErrorInterval && ourNotificationCount.get() < 5;
    if (b) {
      ourErrorTime = System.currentTimeMillis();
      ourNotificationCount.incrementAndGet();
    }
    return b;
  }

  private static void assertSystemPropertiesSet() {
    Map<String, String> props = getOldStyleProperties();

    Map<String, String> was = ourProps.get();
    if (Comparing.equal(was, props) && !itsTime()) {
      return;
    }

    ourProps.set(props);

    String message = getMessageFromProps(props);
    if (message != null) {
      // we only intend to somehow report possible misconfiguration
      // will not show to the user since on macOS this setting is typical
      LOG.info(message);
    }
  }

  @ApiStatus.Internal
  public static @Nullable @NlsContexts.DialogMessage String getMessageFromProps(Map<String, String> props) {
    String message = null;
    for (Map.Entry<String, String> entry : props.entrySet()) {
      if (!Strings.isEmptyOrSpaces(entry.getValue())) {
        message = IdeCoreBundle.message("proxy.old.way.label", entry.getKey(), entry.getValue());
        break;
      }
    }
    return message;
  }

  @ApiStatus.Internal
  public static Map<String, String> getOldStyleProperties() {
    final Map<String, String> props = new HashMap<>();
    props.put(JavaProxyProperty.HTTP_HOST, System.getProperty(JavaProxyProperty.HTTP_HOST));
    props.put(JavaProxyProperty.HTTPS_HOST, System.getProperty(JavaProxyProperty.HTTPS_HOST));
    props.put(JavaProxyProperty.SOCKS_HOST, System.getProperty(JavaProxyProperty.SOCKS_HOST));
    return props;
  }


  /**
   * @deprecated use {@link com.intellij.util.net.JdkProxyProvider#ensureDefault()}
   */
  @ApiStatus.Internal
  @Deprecated
  public void ensureAuthenticator() {
    if (CommonProxyCompatibility.mainAuthenticator != null) {
      Authenticator.setDefault(CommonProxyCompatibility.mainAuthenticator);
    }
    else {
      LOG.warn("main authenticator is not yet registered");
    }
  }

  /** @deprecated no replacement, existing usages are internal and are no-op since noProxy has no usages */
  @ApiStatus.Internal
  @Deprecated
  public void removeNoProxy(final @NotNull String protocol, final @NotNull String host, final int port) { }

  /**
   * @deprecated no replacement, only two internal usages, and the rule is never removed, logic should be implemented by other means,
   * see {@link com.intellij.util.net.ProxyAuthentication}
   */
  @ApiStatus.Internal
  @Deprecated
  public void noAuthentication(final @NotNull String protocol, final @NotNull String host, final int port) { }

  /**
   * @deprecated see {@link com.intellij.util.net.JdkProxyCustomizer}
   */
  @Deprecated
  public void setCustomAuth(@NotNull String key, @NotNull NonStaticAuthenticator nonStaticAuthenticator) {
    synchronized (myLock) {
      var register = CommonProxyCompatibility.registerCustomAuthenticator;
      if (register != null) {
        LOG.debug("custom auth set: " + key + ", " + nonStaticAuthenticator);
        //noinspection resource
        myCustomAuthRegistrations.put(key, register.invoke(nonStaticAuthenticator.asAuthenticator()));
      }
    }
  }

  /**
   * @deprecated see {@link com.intellij.util.net.JdkProxyCustomizer}
   */
  @Deprecated
  public void removeCustomAuth(final @NotNull String key) {
    synchronized (myLock) {
      LOG.debug("custom auth removed: " + key);
      @SuppressWarnings("resource")
      var registration = myCustomAuthRegistrations.remove(key);
      if (registration != null) registration.finish();
    }
  }

  public @NotNull List<Proxy> select(@NotNull URL url) {
    return select(createUri(url));
  }

  @Override
  public @NotNull List<Proxy> select(@Nullable URI uri) {
    ProxySelector mainProxySelector = CommonProxyCompatibility.mainProxySelector;
    if (mainProxySelector == null) {
      LOG.warn("main proxy selector is not yet installed");
      return NO_PROXY_LIST;
    }
    isInstalledAssertion();
    if (uri == null) {
      return NO_PROXY_LIST;
    }
    return mainProxySelector.select(uri);
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    var mainProxySelector = CommonProxyCompatibility.mainProxySelector;
    if (mainProxySelector == null) return;
    mainProxySelector.connectFailed(uri, sa, ioe);
  }

  /** @deprecated use {@link com.intellij.util.net.JdkProxyProvider#getAuthenticator()} */
  @Deprecated
  public Authenticator getAuthenticator() {
    return CommonProxyCompatibility.mainAuthenticator != null ? CommonProxyCompatibility.mainAuthenticator : Authenticator.getDefault();
  }

  /**
   * @apiNote no external usages
   * @deprecated use {@link com.intellij.util.net.ProxyUtils#getHostNameReliably(String, InetAddress, URL)} (it is nullable now)
   */
  @Deprecated
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

  /** @deprecated one external usage in the method that is deprecated, remove after migration */
  @Deprecated
  public static final class HostInfo {
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
      return myPort == info.myPort && myHost.equals(info.myHost) && Objects.equals(myProtocol, info.myProtocol);
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
