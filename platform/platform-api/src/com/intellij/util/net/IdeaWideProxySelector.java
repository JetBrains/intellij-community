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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 1/30/13
* Time: 5:24 PM
*/
public class IdeaWideProxySelector extends ProxySelector {
  private final static Logger LOG = Logger.getInstance("#com.intellij.util.net.IdeaWideProxySelector");
  private final HttpConfigurable myHttpConfigurable;
  private final AtomicReference<ProxySelector> myPacProxySelector = new AtomicReference<ProxySelector>();

  public IdeaWideProxySelector(HttpConfigurable configurable) {
    myHttpConfigurable = configurable;
  }

  @Override
  public List<Proxy> select(@NotNull URI uri) {
    LOG.debug("IDEA-wide proxy selector asked for " + uri.toString());
    final String scheme = uri.getScheme();
    if (! ("http".equals(scheme) || "https".equals(scheme))) {
      LOG.debug("IDEA-wide proxy selector returns no proxies: not http/https scheme: " + scheme);
      return CommonProxy.NO_PROXY_LIST;
    }
    if (myHttpConfigurable.USE_HTTP_PROXY) {
      if (isProxyException(uri)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("IDEA-wide proxy selector detected that uri matches proxy exceptions: uri: " + uri.toString() +
                    ", proxy exceptions string: '" + myHttpConfigurable.PROXY_EXCEPTIONS + "'");
        }
        return CommonProxy.NO_PROXY_LIST;
      }
      final Proxy proxy = new Proxy(myHttpConfigurable.PROXY_TYPE_IS_SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP,
                                    new InetSocketAddress(myHttpConfigurable.PROXY_HOST, myHttpConfigurable.PROXY_PORT));
      LOG.debug("IDEA-wide proxy selector returns defined proxy: " + proxy);
      myHttpConfigurable.LAST_ERROR = null;
      return Collections.singletonList(proxy);
    } else if (myHttpConfigurable.USE_PROXY_PAC) {
      ProxySelector pacProxySelector = myPacProxySelector.get();
      if (pacProxySelector == null) {
        final ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
        proxySearch.setPacCacheSettings(32, 10 * 60 * 1000); // Cache 32 urls for up to 10 min.
        pacProxySelector = proxySearch.getProxySelector();

        myPacProxySelector.lazySet(pacProxySelector);
      }

      if (pacProxySelector != null) {
        final List<Proxy> select = pacProxySelector.select(uri);
        LOG.debug("IDEA-wide proxy selector found autodetected proxies: " + select);
        return select;
      }
      LOG.debug("IDEA-wide proxy selector found no autodetected proxies");
    }
    return CommonProxy.NO_PROXY_LIST;
  }

  private boolean isProxyException(URI uri) {
    String uriHost = uri.getHost();
    if (StringUtil.isEmptyOrSpaces(uriHost)) return false;
    if (StringUtil.isEmptyOrSpaces(myHttpConfigurable.PROXY_EXCEPTIONS)) return false;
    final List<String> hosts = StringUtil.split(myHttpConfigurable.PROXY_EXCEPTIONS, ",");
    for (String hostPattern : hosts) {
      final String regexpPattern = StringUtil.escapeToRegexp(hostPattern.trim()).replace("\\*", ".*");
      if (Pattern.compile(regexpPattern).matcher(uriHost).matches()) return true;
    }
    return false;
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
