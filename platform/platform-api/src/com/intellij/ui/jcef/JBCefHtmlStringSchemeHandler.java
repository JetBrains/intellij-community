// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A custom scheme handler for serving the {@link JBCefBrowser#loadHTML(String, String)} method.
 *
 * @author tav
 */
class JBCefHtmlStringSchemeHandler extends CefResourceHandlerAdapter implements Disposable {
  private static final Logger LOG = Logger.getInstance(JBCefHtmlStringSchemeHandler.class);

  public static final String HTML_STRING_SCHEME_NAME = "jb-html-string";

  private static final Map<CefBrowser, Map<String/* url */, String /* html */>> LOAD_REQUEST_MAP = new HashMap<>();

  @NotNull private final CefBrowser myBrowser;
  @Nullable private InputStream myInputStream;

  JBCefHtmlStringSchemeHandler(@NotNull CefBrowser browser, @SuppressWarnings("unused") @NotNull CefFrame frame) {
    myBrowser = browser;
    Disposer.register(JBCefBrowser.getJBCefBrowser(browser), this);
  }

  @Override
  public void dispose() {
    LOAD_REQUEST_MAP.remove(myBrowser);
  }

  public static void registerRequest(@NotNull CefBrowser browser, @NotNull String html, @NotNull String url) {
    getInitMap(browser).put(url, html);
  }

  @NotNull
  private static Map<String, String> getInitMap(@NotNull CefBrowser browser) {
    Map<String, String> map = LOAD_REQUEST_MAP.get(browser);
    if (map == null) {
      synchronized (LOAD_REQUEST_MAP) {
        map = LOAD_REQUEST_MAP.get(browser);
        if (map == null) {
          LOAD_REQUEST_MAP.put(browser, map = Collections.synchronizedMap(new HashMap<>()));
        }
      }
    }
    return map;
  }

  @Override
  public boolean processRequest(@NotNull CefRequest request, @NotNull CefCallback callback) {
    Map<String, String> map = LOAD_REQUEST_MAP.get(myBrowser);
    if (map != null) {
      String html = map.get(request.getURL());
      if (html != null) {
        myInputStream = new ByteArrayInputStream(html.getBytes(Charset.defaultCharset()));
        callback.Continue();
        return true;
      }
    }
    return false;
  }

  @Override
  public void getResponseHeaders(@NotNull CefResponse response, IntRef response_length, StringRef redirectUrl) {
    response.setMimeType("text/html");
    if (myInputStream == null) {
      response.setError(CefLoadHandler.ErrorCode.ERR_INSUFFICIENT_RESOURCES);
      response.setStatusText("The HTML string is null");
      LOG.error("JBCefHtmlStringSchemeHandler.getResponseHeaders: the HTML string is null");
      response.setStatus(400);
      return;
    }
    response.setStatus(200);
  }

  @Override
  public boolean readResponse(byte@NotNull[] data_out, int bytes_to_read, IntRef bytes_read, CefCallback callback) {
    boolean inProgress = JBCefFileSchemeHandler.readResponse(myInputStream, data_out, bytes_to_read, bytes_read, callback);
    if (!inProgress) {
      myInputStream = null;
    }
    return inProgress;
  }
}
