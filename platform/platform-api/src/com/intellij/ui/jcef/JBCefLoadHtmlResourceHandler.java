// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;

/**
 * Serves {@link JBCefBrowser#loadHTML(String, String) requests. See {@link JBCefFileSchemeHandlerFactory}.
 *
 * @author tav
 */
class JBCefLoadHtmlResourceHandler extends CefResourceHandlerAdapter {
  private static final Logger LOG = Logger.getInstance(JBCefLoadHtmlResourceHandler.class.getName());

  @NotNull private final InputStream myInputStream;

  JBCefLoadHtmlResourceHandler(@NotNull String html) {
    myInputStream = new ByteArrayInputStream(html.getBytes(Charset.defaultCharset()));
  }

  @Override
  public boolean processRequest(@NotNull CefRequest request, @NotNull CefCallback callback) {
    callback.Continue();
    return true;
  }

  @Override
  public void getResponseHeaders(@NotNull CefResponse response, IntRef response_length, StringRef redirectUrl) {
    response.setMimeType("text/html");
    response.setStatus(200);
  }

  @Override
  public boolean readResponse(byte@NotNull[] data_out, int bytes_to_read, IntRef bytes_read, CefCallback callback) {
    try {
      int availableSize = myInputStream.available();
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
    try {
      myInputStream.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return false;
  }
}
