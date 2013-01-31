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
import com.intellij.util.proxy.CommonProxy;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 1/30/13
* Time: 5:24 PM
*/
public class IdeaWideProxySelector extends ProxySelector {
  private final static Logger LOG = Logger.getInstance("#com.intellij.util.net.IdeaWideProxySelector");
  private final HttpConfigurable myHttpConfigurable;

  public IdeaWideProxySelector(HttpConfigurable configurable) {
    myHttpConfigurable = configurable;
  }

  @Override
  public List<Proxy> select(URI uri) {
    LOG.debug("IDEA-wide proxy selector asked for " + uri.toString());
    final String scheme = uri.getScheme();
    if (! ("http".equals(scheme) || "https".equals(scheme))) {
      LOG.debug("IDEA-wide proxy selector returns no proxies: not http/https scheme: " + scheme);
      return CommonProxy.NO_PROXY_LIST;
    }
    if (myHttpConfigurable.USE_HTTP_PROXY) {
      final Proxy proxy = new Proxy(myHttpConfigurable.PROXY_TYPE_IS_SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP,
                                    new InetSocketAddress(myHttpConfigurable.PROXY_HOST, myHttpConfigurable.PROXY_PORT));
      LOG.debug("IDEA-wide proxy selector returns defined proxy: " + proxy);
      myHttpConfigurable.LAST_ERROR = null;
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
