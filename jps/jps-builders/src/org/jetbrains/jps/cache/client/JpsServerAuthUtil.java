// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.client;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class JpsServerAuthUtil {
  private static final Logger LOG = Logger.getInstance(JpsServerAuthUtil.class);
  private static final Object myLock = new Object();
  private static Map<String, String> requestHeaders;

  public static Map<String, String> getRequestHeaders(@NotNull JpsNettyClient nettyClient) {
    synchronized (myLock) {
      try {
        nettyClient.requestAuthToken();
        myLock.wait();
      }
      catch (InterruptedException e) {
        LOG.warn("Can't request authentication token", e);
      }
    }
    return requestHeaders;
  }

  public static void setRequestHeaders(Map<String, String> requestHeaders) {
    synchronized (myLock) {
      JpsServerAuthUtil.requestHeaders = requestHeaders;
      myLock.notify();
    }
  }
}
