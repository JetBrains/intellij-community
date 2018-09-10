// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net;

import com.github.markusbernhardt.proxy.ProxySearch;
import com.github.markusbernhardt.proxy.selector.misc.BufferedProxySelector;
import com.github.markusbernhardt.proxy.selector.pac.PacProxySelector;
import com.github.markusbernhardt.proxy.selector.pac.UrlPacScriptSource;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
* @author Irina.Chernushina
* @since 1/30/13
*/
public class IdeaWideProxySelector extends ProxySelector {
  private final static Logger LOG = Logger.getInstance("#com.intellij.util.net.IdeaWideProxySelector");

  private final HttpConfigurable myHttpConfigurable;
  private final AtomicReference<Pair<ProxySelector, String>> myPacProxySelector = new AtomicReference<>();

  public IdeaWideProxySelector(HttpConfigurable configurable) {
    myHttpConfigurable = configurable;
  }

  @Override
  public List<Proxy> select(@NotNull URI uri) {
    LOG.debug("IDEA-wide proxy selector asked for " + uri.toString());

    String scheme = uri.getScheme();
    if (!("http".equals(scheme) || "https".equals(scheme))) {
      LOG.debug("No proxy: not http/https scheme: " + scheme);
      return CommonProxy.NO_PROXY_LIST;
    }

    if (myHttpConfigurable.USE_HTTP_PROXY) {
      if (myHttpConfigurable.isProxyException(uri)) {
        LOG.debug("No proxy: URI '", uri, "' matches proxy exceptions [", myHttpConfigurable.PROXY_EXCEPTIONS, "]");
        return CommonProxy.NO_PROXY_LIST;
      }

      if (myHttpConfigurable.PROXY_PORT < 0 || myHttpConfigurable.PROXY_PORT > 65535) {
        LOG.debug("No proxy: invalid port: " + myHttpConfigurable.PROXY_PORT);
        return CommonProxy.NO_PROXY_LIST;
      }

      Proxy.Type type = myHttpConfigurable.PROXY_TYPE_IS_SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
      Proxy proxy = new Proxy(type, new InetSocketAddress(myHttpConfigurable.PROXY_HOST, myHttpConfigurable.PROXY_PORT));
      LOG.debug("Defined proxy: ", proxy);
      myHttpConfigurable.LAST_ERROR = null;
      return Collections.singletonList(proxy);
    }

    if (myHttpConfigurable.USE_PROXY_PAC) {
      // It is important to avoid resetting Pac based ProxySelector unless option was changed
      // New instance will download configuration file and interpret it before making the connection
      String pacUrlForUse = myHttpConfigurable.USE_PAC_URL && !StringUtil.isEmpty(myHttpConfigurable.PAC_URL)? myHttpConfigurable.PAC_URL : null;
      Pair<ProxySelector, String> pair = myPacProxySelector.get();
      if (pair != null && !Comparing.equal(pair.second, pacUrlForUse)) {
        pair = null;
      }

      if (pair == null) {
        ProxySelector newProxySelector;
        if (pacUrlForUse != null) {
          newProxySelector = new PacProxySelector(new UrlPacScriptSource(pacUrlForUse));
        } else {
          ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
          proxySearch.setPacCacheSettings(32, 10 * 60 * 1000, BufferedProxySelector.CacheScope.CACHE_SCOPE_HOST); // Cache 32 urls for up to 10 min.
          newProxySelector = proxySearch.getProxySelector();
        }
        pair = Pair.create(newProxySelector, pacUrlForUse);
        myPacProxySelector.lazySet(pair);
      }

      ProxySelector pacProxySelector = pair.first;

      if (pacProxySelector != null) {
        try {
          List<Proxy> select = pacProxySelector.select(uri);
          LOG.debug("Autodetected proxies: ", select);
          return select;
        } catch (StackOverflowError ignore) {
          LOG.debug("Too large PAC script (JRE-247)");
        }
      }
      else {
        LOG.debug("No proxies detected");
      }
    }

    return CommonProxy.NO_PROXY_LIST;
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    if (myHttpConfigurable.USE_PROXY_PAC) {
      myHttpConfigurable.removeGeneric(new CommonProxy.HostInfo(uri.getScheme(), uri.getHost(), uri.getPort()));
      LOG.debug("generic proxy credentials (if were saved) removed");
      return;
    }

    final InetSocketAddress isa = sa instanceof InetSocketAddress ? (InetSocketAddress) sa : null;
    if (myHttpConfigurable.USE_HTTP_PROXY && isa != null && Comparing.equal(myHttpConfigurable.PROXY_HOST, isa.getHostName())) {
      LOG.debug("connection failed message passed to http configurable");
      myHttpConfigurable.LAST_ERROR = ioe.getMessage();
    }
  }
}
