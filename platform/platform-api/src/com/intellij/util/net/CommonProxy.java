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
package com.intellij.util.net;

import com.btr.proxy.search.ProxySearch;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/21/13
 * Time: 12:39 PM
 */
public class CommonProxy extends ProxySelector {
  private final static CommonProxy ourInstance = new CommonProxy();
  private final IdeaWideProxySelector myIDEAWide = new IdeaWideProxySelector();
  private final static List<Proxy> ourNoProxy = Collections.singletonList(Proxy.NO_PROXY);
  private volatile static ProxySelector ourWrong;
  private static final Map<String, String> ourProps = new HashMap<String, String>();
  static {
    ProxySelector.setDefault(ourInstance);
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.net.CommonProxy");
  private final Object myLock;
  private final Set<HostInfo> myNoProxy;

  private final Map<String, ProxySelector> myCustom;
  private final Map<Pair<String, Integer>, NonStaticAuthenticator> myCustomAuth;

  public static CommonProxy getInstance() {
    return ourInstance;
  }

  public CommonProxy() {
    myLock = new Object();
    myNoProxy = new HashSet<HostInfo>();
    myCustom = new HashMap<String, ProxySelector>();
    myCustomAuth = new HashMap<Pair<String, Integer>, NonStaticAuthenticator>();
  }

  public static void isInstalledAssertion() {
    final ProxySelector aDefault = ProxySelector.getDefault();
    if (ourInstance != aDefault) {
      // to report only once
      if (ourWrong != aDefault) {
        LOG.error("ProxySelector.setDefault() was changed to [" + aDefault.toString() + "] - other than com.intellij.util.net.CommonProxy.ourInstance.\n" +
                  "This will make some " + ApplicationNamesInfo.getInstance().getProductName() + " network calls fail.\n" +
                  "Instead, methods of com.intellij.util.net.CommonProxy should be used for proxying.");
        ourWrong = aDefault;
      }
      ProxySelector.setDefault(ourInstance);
      ourInstance.ensureAuthenticator();
    }
    assertSystemPropertiesSet();
  }

  private static void assertSystemPropertiesSet() {
    final Map<String, String> props = new HashMap<String, String>();
    props.put(JavaProxyProperty.HTTP_HOST, System.getProperty(JavaProxyProperty.HTTP_HOST));
    props.put(JavaProxyProperty.HTTPS_HOST, System.getProperty(JavaProxyProperty.HTTPS_HOST));
    props.put(JavaProxyProperty.SOCKS_HOST, System.getProperty(JavaProxyProperty.SOCKS_HOST));

    if (Comparing.equal(ourProps, props)) return;
    ourProps.clear();
    ourProps.putAll(props);

    for (Map.Entry<String, String> entry : props.entrySet()) {
      if (! StringUtil.isEmptyOrSpaces(entry.getValue())) {
        final String message = "You have JVM property '" + entry.getKey() + "' set to '" + entry.getValue() + "'.\n" +
                               "This may lead to incorrect behaviour. Proxy should be set in Settings | " + HTTPProxySettingsPanel.NAME;
        PopupUtil.showBalloonForActiveComponent(message, MessageType.WARNING);
        LOG.info(message);
      }
    }
  }

  public void ensureAuthenticator() {
    myIDEAWide.resetAuthenticator();
  }

  public void noProxy(@NotNull final String protocol, @NotNull final String host, final int port) {
    synchronized (myLock) {
      LOG.debug("no proxy added: " + protocol + "://" + host + ":" + port);
      myNoProxy.add(new HostInfo(protocol, host, port));
    }
  }

  public void removeNoProxy(@NotNull final String protocol, @NotNull final String host, final int port) {
    synchronized (myLock) {
      LOG.debug("no proxy removed: " + protocol + "://" + host + ":" + port);
      myNoProxy.remove(new HostInfo(protocol, host, port));
    }
  }

  public void setCustom(@NotNull final String key, @NotNull final ProxySelector proxySelector) {
    synchronized (myLock) {
      LOG.debug("custom set: " + key + ", " + proxySelector.toString());
      myCustom.put(key, proxySelector);
    }
  }

  public void setCustomAuth(@NotNull final String host, final int port, final NonStaticAuthenticator authenticator) {
    synchronized (myLock) {
      LOG.debug("custom auth set: " + host + ":" + port + ", " + authenticator.toString());
      myCustomAuth.put(Pair.create(host, port), authenticator);
    }
  }

  public void removeCustomAuth(@NotNull final String host, final int port) {
    synchronized (myLock) {
      LOG.debug("custom auth removed: " + host + ":" + port);
      myCustomAuth.remove(Pair.create(host, port));
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
  public List<Proxy> select(URI uri) {
    LOG.debug("CommonProxy.select called for " + uri.toString());
    isInstalledAssertion();
    myIDEAWide.resetAuthenticator();

    final String host = uri.getHost();
    final int port = uri.getPort();
    final String protocol = uri.getScheme();

    final HostInfo info = new HostInfo(protocol, host, port);
    final Map<String, ProxySelector> copy;
    synchronized (myLock) {
      if (myNoProxy.contains(info)) {
        LOG.debug("CommonProxy.select returns no proxy (in no proxy list) for " + uri.toString());
        return ourNoProxy;
      }
      copy = new HashMap<String, ProxySelector>(myCustom);
    }
    for (Map.Entry<String, ProxySelector> entry : copy.entrySet()) {
      final List<Proxy> proxies = entry.getValue().select(uri);
      if (proxies != null && proxies.size() > 0) {
        LOG.debug("CommonProxy.select returns custom proxy for " + uri.toString() + ", " + proxies.toString());
        return proxies;
      }
    }
    // delegate to IDEA-wide behaviour
    final List<Proxy> selected = myIDEAWide.select(uri);
    if (myIDEAWide.myHttpConfigurable.USE_HTTP_PROXY) {
      myIDEAWide.myHttpConfigurable.LAST_ERROR = null;
    }
    LOG.debug("CommonProxy.select returns something after common platform check for " + uri.toString() + ", " + selected.toString());
    return selected;
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    LOG.info("connect failed to " + uri.toString() + ", sa: " + sa.toString(), ioe);
    myIDEAWide.myHttpConfigurable.removeGeneric(new HostInfo(uri.getScheme(), uri.getHost(), uri.getPort()));
    final InetSocketAddress isa = sa instanceof InetSocketAddress ? (InetSocketAddress) sa : null;
    if (myIDEAWide.myHttpConfigurable.USE_HTTP_PROXY && isa != null && Comparing.equal(myIDEAWide.myHttpConfigurable.PROXY_HOST, isa.getHostName())) {
      LOG.debug("connection failed message passed to http configurable");
      myIDEAWide.myHttpConfigurable.LAST_ERROR = ioe.getMessage();
    }
  }

  private class CommonAuthenticator extends Authenticator {
    private final HttpConfigurable myHttpConfigurable;

    CommonAuthenticator() {
      myHttpConfigurable = HttpConfigurable.getInstance();
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      final String siteStr = getRequestingSite() == null ? null : getRequestingSite().toString();
      LOG.debug("CommonAuthenticator.getPasswordAuthentication called for " + siteStr);
      String host = getRequestingHost();
      if (host == null) {
        final InetAddress site = getRequestingSite();
        if (site != null) {
          host = site.getHostName();
        } else if (getRequestingURL() != null) {
          host = getRequestingURL().getHost();
        }
      }
      final int port = getRequestingPort();

      final Map<Pair<String, Integer>, NonStaticAuthenticator> copy;
      synchronized (myLock) {
        // for hosts defined as no proxy we will NOT pass authentication to not provoke credentials
        if (myNoProxy.contains(new HostInfo(getRequestingProtocol(), host, port))) {
          LOG.debug("CommonAuthenticator.getPasswordAuthentication found host in no proxies list (" + siteStr + ")");
          return null;
        }
        copy = new HashMap<Pair<String, Integer>, NonStaticAuthenticator>(myCustomAuth);
      }

      if (! copy.isEmpty()) {
        final Pair<String, Integer> hostInfo = Pair.create(getRequestingHost(), getRequestingPort());
        final NonStaticAuthenticator authenticator1 = copy.get(hostInfo);
        if (authenticator1 != null) {
          prepareAuthenticator(authenticator1);
          LOG.debug("CommonAuthenticator.getPasswordAuthentication found custom authenticator for " + siteStr + ", " + authenticator1.toString());
          final PasswordAuthentication authentication = authenticator1.getPasswordAuthentication();
          logAuthentication(authentication);
          return authentication;
        }
      }

      // according to idea-wide settings
      if (myHttpConfigurable.USE_HTTP_PROXY) {
        LOG.debug("CommonAuthenticator.getPasswordAuthentication will return common defined proxy");
        final PasswordAuthentication authentication =
          myHttpConfigurable.getPromptedAuthentication(getRequestingHost() + ":" + getRequestingPort(), getRequestingPrompt());
        logAuthentication(authentication);
        return authentication;
      } else if (myHttpConfigurable.USE_PROXY_PAC) {
        LOG.debug("CommonAuthenticator.getPasswordAuthentication will return autodetected proxy");
        // same but without remembering the results..
        final PasswordAuthentication password = myHttpConfigurable.getGenericPassword(getRequestingHost(), getRequestingPort());
        if (password != null) {
          logAuthentication(password);
          return password;
        }

        final PasswordAuthentication authentication =
          myHttpConfigurable.getGenericPromptedAuthentication(getRequestingHost(), getRequestingPrompt(), getRequestingPort(), true);
        logAuthentication(authentication);
        return authentication;
      } else {
        LOG.debug("CommonAuthenticator.getPasswordAuthentication generic authentication will be asked");
        final PasswordAuthentication authentication =
          myHttpConfigurable.getGenericPromptedAuthentication(getRequestingHost(), getRequestingPrompt(), getRequestingPort(), false);
        logAuthentication(authentication);
        return authentication;
      }
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
  }

  private void logAuthentication(PasswordAuthentication authentication) {
    if (authentication == null) {
      LOG.debug("CommonAuthenticator.getPasswordAuthentication returned null");
    } else {
      LOG.debug("CommonAuthenticator.getPasswordAuthentication returned authentication pair with login: " + authentication.getUserName());
    }
  }

  private static URI createUri(final URL url) {
    final URI uri;
    try {
      uri = new URI(url.toString());
    }
    catch (URISyntaxException e) {
      LOG.info(e);
      throw new RuntimeException(e);
    }
    return uri;
  }

  private class IdeaWideProxySelector extends ProxySelector {
    private final HttpConfigurable myHttpConfigurable;
    private final CommonAuthenticator myAuthenticator;

    private IdeaWideProxySelector() {
      myHttpConfigurable = HttpConfigurable.getInstance();
      myAuthenticator = new CommonAuthenticator();
      Authenticator.setDefault(myAuthenticator);
    }

    void resetAuthenticator() {
      Authenticator.setDefault(myAuthenticator);
    }

    @Override
    public List<Proxy> select(URI uri) {
      LOG.debug("IDEA-wide proxy selector asked for " + uri.toString());
      final String scheme = uri.getScheme();
      if (! ("http".equals(scheme) || "https".equals(scheme))) {
        LOG.debug("IDEA-wide proxy selector returns no proxies: not http/https scheme: " + scheme);
        return ourNoProxy;
      }
      if (myHttpConfigurable.USE_HTTP_PROXY) {
        final Proxy proxy = new Proxy(myHttpConfigurable.PROXY_TYPE_IS_SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP,
                                      new InetSocketAddress(myHttpConfigurable.PROXY_HOST, myHttpConfigurable.PROXY_PORT));
        LOG.debug("IDEA-wide proxy selector returns defined proxy: " + proxy);
        return Collections.singletonList(proxy);
      } else if (myHttpConfigurable.USE_PROXY_PAC) {
        final ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
        final ProxySelector proxySelector = proxySearch.getProxySelector();
        if (proxySelector != null) {
          final List<Proxy> select = proxySelector.select(uri);
          LOG.debug("IDEA-wide proxy selector found autodetected proxies: " + select);
          return select;
        }
        LOG.debug("IDEA-wide proxy selector found no autodetected proxies");
      }
      return ourNoProxy;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
      // never delegated to
      assert false;
    }
  }

  public static class HostInfo {
    public String myProtocol;
    public String myHost;
    public int myPort;

    public HostInfo() {
    }

    public HostInfo(String protocol, String host, int port) {
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
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HostInfo info = (HostInfo)o;

      if (myPort != info.myPort) return false;
      if (!myHost.equals(info.myHost)) return false;
      if (myProtocol != null ? !myProtocol.equals(info.myProtocol) : info.myProtocol != null) return false;

      return true;
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
