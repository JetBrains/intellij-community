// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.google.common.base.CharMatcher;
import com.intellij.openapi.diagnostic.Logger;
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
import java.nio.file.*;

/**
 * A "file:" scheme handler for reading resource files.
 *
 * @author tav
 */
class JBCefFileSchemeHandler extends CefResourceHandlerAdapter {
  private static final Logger LOG = Logger.getInstance(JBCefFileSchemeHandler.class);

  public static final String FILE_SCHEME_NAME = "file";

  @NotNull private final CefBrowser myBrowser;
  @NotNull private final CefFrame myFrame;

  @Nullable private Path myPath;
  @Nullable private InputStream myInputStream;

  JBCefFileSchemeHandler(@NotNull CefBrowser browser, @NotNull CefFrame frame) {
    myBrowser = browser;
    myFrame = frame;
  }

  @Override
  public boolean processRequest(@NotNull CefRequest request, @NotNull CefCallback callback) {
    String url = request.getURL();
    if (url != null) {
      try {
        myPath = Paths.get(new URI(MyUriUtil.trimParameters(url)));
        if (!checkAccessAllowed(myPath)) {
          LOG.info("Access denied: " + myPath);
          return false;
        }
      }
      catch (IllegalArgumentException | FileSystemNotFoundException | URISyntaxException e) {
        LOG.error(e);
      }
    }
    if (myPath != null) {
      callback.Continue();
      return true;
    }
    return false;
  }

  @Override
  public void getResponseHeaders(@NotNull CefResponse response, IntRef response_length, StringRef redirectUrl) {
    if (myPath != null) {
      try {
        response.setMimeType(Files.probeContentType(myPath));
        myInputStream = new BufferedInputStream(new FileInputStream(myPath.toFile()));
      }
      catch (IOException | UnsupportedOperationException e) {
        response.setError(CefLoadHandler.ErrorCode.ERR_FILE_NOT_FOUND);
        response.setStatusText(e.getLocalizedMessage());
        LOG.error(e);
      }
    }
    response.setStatus(myInputStream != null ? 200 : 404);
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
    myPath = null;
    if (myInputStream != null) {
      try {
        myInputStream.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      myInputStream = null;
    }
    return false;
  }

  private static boolean checkAccessAllowed(Path path) {
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
