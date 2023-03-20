// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.client;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class JpsServerAuthUtil {
  private static Map<String, String> requestHeaders;

  public static @NotNull Map<String, String> getRequestHeaders() {
    return requestHeaders;
  }

  public static void setRequestHeaders(@NotNull Map<String, String> requestHeaders) {
    JpsServerAuthUtil.requestHeaders = requestHeaders;
  }
}
