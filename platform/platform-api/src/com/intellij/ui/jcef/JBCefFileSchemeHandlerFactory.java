// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * A factory for the custom "file" scheme handler.
 * <p>
 * CEF allows for loading local resources via "file" only from inside another "file" request.
 * Thus {@link JBCefBrowser#loadHTML(String, String)} creates a fake "file" request (proxied via this factory)
 * in order to allow for loading resource files from the passed html string.
 * <p>
 * All the standard "file" requests are handled by default CEF handler with default security policy.
 *
 * @author tav
 */
final class JBCefFileSchemeHandlerFactory implements CefSchemeHandlerFactory  {
  public static final String FILE_SCHEME_NAME = "file";
  public static final String LOADHTML_RANDOM_URL_PREFIX = FILE_SCHEME_NAME + ":///jbcefbrowser/";

  public static final Map<CefBrowser, Map<String/* url */, String /* html */>> LOADHTML_REQUEST_MAP = ContainerUtil.createWeakMap();

  @Override
  public CefResourceHandler create(@NotNull CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
    if (!FILE_SCHEME_NAME.equals(schemeName)) return null;

    String url = request.getURL();
    if (url == null) return null;

    // 1) check if the request has been registered
    Map<String, String> map = LOADHTML_REQUEST_MAP.get(browser);
    if (map != null) {
      String html = map.remove(request.getURL());
      if (html != null) {
        return new JBCefLoadHtmlResourceHandler(html);
      }
    }
    // 2) otherwise allow default handling (with default CEF security)
    return null;
  }

  @NotNull
  public static String registerLoadHTMLRequest(@NotNull CefBrowser browser, @NotNull String html, @NotNull String origUrl) {
    String fileUrl = makeFileUrl(origUrl);
    getInitMap(browser).put(fileUrl, html);
    return fileUrl;
  }

  @NotNull
  private static Map<String, String> getInitMap(@NotNull CefBrowser browser) {
    Map<String, String> map = LOADHTML_REQUEST_MAP.get(browser);
    if (map == null) {
      synchronized (LOADHTML_REQUEST_MAP) {
        map = LOADHTML_REQUEST_MAP.get(browser);
        if (map == null) {
          LOADHTML_REQUEST_MAP.put(browser, map = Collections.synchronizedMap(new HashMap<>()));
        }
      }
    }
    return map;
  }

  @NotNull
  public static String makeFileUrl(@NotNull String url) {
    if (url.startsWith(FILE_SCHEME_NAME + URLUtil.SCHEME_SEPARATOR)) {
      return url;
    }
    // otherwise make a random file:// url
    return LOADHTML_RANDOM_URL_PREFIX + new Random().nextInt(Integer.MAX_VALUE) + "#url=" + url;
  }
}
