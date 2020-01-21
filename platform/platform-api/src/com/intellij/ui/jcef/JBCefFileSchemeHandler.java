// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
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
import java.util.function.Function;

/**
 * A custom scheme handler for reading resource files.
 *
 * @author tav
 */
public class JBCefFileSchemeHandler extends CefResourceHandlerAdapter {
  private static final Logger LOG = Logger.getInstance(JBCefFileSchemeHandler.class);

  @NotNull private final String mySchemeName;
  @NotNull private final Function<String, Boolean> myPathValidator;
  @Nullable private String myPath;
  @Nullable private InputStream myInputStream;

  private JBCefFileSchemeHandler(@NotNull String customSchemeName, @NotNull Function<String, Boolean> pathValidator) {
    mySchemeName = customSchemeName;
    myPathValidator = pathValidator;
  }

  /**
   * @param customSchemeName the scheme name
   * @param pathValidator a validator that assures the file path addresses a valid and safe resource file
   */
  public static JBCefFileSchemeHandler create(@NotNull String customSchemeName, @NotNull Function<String, Boolean> pathValidator) {
    // [tav] todo: think if we should restrict file access to the caller
    return new JBCefFileSchemeHandler(customSchemeName, pathValidator);
  }

  @Override
  public boolean processRequest(@NotNull CefRequest request, @NotNull CefCallback callback) {
    String url = request.getURL();
    if (url != null) {
      try {
        URI uri = new URI(url);
        myPath = (SystemInfoRt.isWindows ? uri.getHost() + ":" : "/" + uri.getHost()) + uri.getPath();
        if (!myPathValidator.apply(myPath)) {
          LOG.info("The addressed resource file hasn't passed validation: " + myPath);
          return false;
        }
      }
      catch (URISyntaxException e) {
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
        File file = new File(myPath);
        response.setMimeType(Files.probeContentType(file.toPath()));
        myInputStream = new BufferedInputStream(new FileInputStream(file));
      }
      catch (IOException | InvalidPathException e) {
        response.setError(CefLoadHandler.ErrorCode.ERR_FILE_NOT_FOUND);
        response.setStatusText(e.getLocalizedMessage());
        LOG.error(e);
      }
    }
    response.setStatus(myInputStream != null ? 200 : 404);
  }

  @Override
  public boolean readResponse(@NotNull byte[] data_out, int bytes_to_read, IntRef bytes_read, CefCallback callback) {
    try {
      int availableSize = myInputStream != null ? myInputStream.available() : 0;
      if (availableSize > 0) {
        int bytesToRead = Math.min(bytes_to_read, availableSize);
        bytesToRead = myInputStream.read(data_out, 0, bytesToRead);
        bytes_read.set(bytesToRead);
        return true;
      }
      myPath = null;
      bytes_read.set(0);
      if (myInputStream != null) {
        myInputStream.close();
        myInputStream = null;
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return false;
  }

  @Override
  public String toString() {
    return JBCefFileSchemeHandler.class.getName() + "[scheme:" + mySchemeName + "]@" + hashCode();
  }
}
