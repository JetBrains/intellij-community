// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.google.common.base.CharMatcher;
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

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A custom scheme handler for the "file" scheme.
 * <p>
 * CEF allows for loading local resources via "file" only from inside another "file" request.
 * Thus {@link JBCefBrowser#loadHTML(String)} creates a fake "file" request (proxied via this custom handler)
 * in order to allow for loading resource files from the passed html string.
 * <p>
 * As the handler processes all the "file" requests, it also processes standard "file" requests.
 *
 * @author tav
 */
class JBCefFileSchemeHandler extends CefResourceHandlerAdapter implements Disposable {
  private static final Logger LOG = Logger.getInstance(JBCefFileSchemeHandler.class.getName());

  public static final String FILE_SCHEME_NAME = "file";
  private static final String LOADHTML_URL_PREFIX = FILE_SCHEME_NAME + ":///intellij/jbcefbrowser/loadhtml";

  private static final Map<CefBrowser, Map<String/* url */, String /* html */>> LOADHTML_REQUEST_MAP = new HashMap<>();

  @NotNull private final CefBrowser myBrowser;
  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  @NotNull private final CefFrame myFrame;
  @Nullable protected InputStream myInputStream;

  @Nullable private String myMimeType;
  @Nullable private CefLoadHandler.ErrorCode myErrorCode;
  @Nullable private String myStatusText;

  JBCefFileSchemeHandler(@NotNull CefBrowser browser, @NotNull CefFrame frame) {
    myBrowser = browser;
    myFrame = frame;
    Disposer.register(JBCefBrowser.getJBCefBrowser(browser), this);
  }

  @NotNull
  public static String registerLoadHTMLRequest(@NotNull CefBrowser browser, @NotNull String html, @NotNull String initUrl) {
    String url = LOADHTML_URL_PREFIX + "#req=" + initUrl;
    getInitMap(browser).put(url, html);
    return url;
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

  @Override
  public void dispose() {
    LOADHTML_REQUEST_MAP.remove(myBrowser);
  }

  @Override
  public boolean processRequest(@NotNull CefRequest request, @NotNull CefCallback callback) {
    String url = request.getURL();
    if (url == null) return false;

    if (url.startsWith(LOADHTML_URL_PREFIX)) {
      //
      // 1) JBCefBrowser.loadHTML() request
      //
      Map<String, String> map = LOADHTML_REQUEST_MAP.get(myBrowser);
      if (map != null) {
        String html = map.get(request.getURL());
        if (html != null) {
          myInputStream = new ByteArrayInputStream(html.getBytes(Charset.defaultCharset()));
          myMimeType = "text/html";
          callback.Continue();
          return true;
        }
      }
      myErrorCode = CefLoadHandler.ErrorCode.ERR_FILE_NOT_FOUND;
      myStatusText = "JBCefBrowser.loadHTML: html not found";
      LOG.error(myStatusText);
    }
    else {
      //
      // 2) Standard "file://" request
      //
      try {
        Path path = Paths.get(new URI(MyUriUtil.trimParameters(url)));
        if (!checkAccessAllowed(path)) {
          LOG.info("Access denied: " + path);
          return false;
        }
        myInputStream = new BufferedInputStream(new FileInputStream(path.toFile()));
        myMimeType = Files.probeContentType(path);
      }
      catch (IllegalArgumentException |
        FileSystemNotFoundException |
        URISyntaxException |
        IOException |
        UnsupportedOperationException e)
      {
        myErrorCode = CefLoadHandler.ErrorCode.ERR_FILE_NOT_FOUND;
        myStatusText = e.getLocalizedMessage();
        LOG.error(e);
      }
      if (myInputStream != null) {
        callback.Continue();
        return true;
      }
    }
    return false;
  }

  @Override
  public void getResponseHeaders(@NotNull CefResponse response, IntRef response_length, StringRef redirectUrl) {
    if (myErrorCode != null) {
      response.setError(myErrorCode);
      response.setStatusText(myStatusText);
      response.setStatus(404);
      return;
    }
    response.setMimeType(myMimeType);
    response.setStatus(200);
  }

  @Override
  public boolean readResponse(byte@NotNull[] data_out, int bytes_to_read, IntRef bytes_read, CefCallback callback) {
    try {
      int availableSize = myInputStream != null ? myInputStream.available() : 0;
      if (availableSize > 0) {
        int bytesToRead = Math.min(bytes_to_read, availableSize);
        bytesToRead = myInputStream.read(data_out, 0, bytesToRead);
        bytes_read.set(bytesToRead);
        return true;
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    bytes_read.set(0);
    if (myInputStream != null) {
      try {
        myInputStream.close();
        myInputStream = null;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return false;
  }

  private static boolean checkAccessAllowed(@SuppressWarnings("unused") Path path) {
    // tav: todo Ask the user or query the settings for JCEF FS access.
    return true;
  }

  // from com.intellij.util.UriUtil
  private static class MyUriUtil {
    public static final CharMatcher PARAM_CHAR_MATCHER = CharMatcher.anyOf("?#;");

    public static String trimParameters(@NotNull String url) {
      int end = PARAM_CHAR_MATCHER.indexIn(url);
      return end != -1 ? url.substring(0, end) : url;
    }
  }
}
