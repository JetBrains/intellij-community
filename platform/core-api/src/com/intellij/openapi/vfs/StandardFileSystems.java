// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.util.io.URLUtil;

import java.util.function.Supplier;

public final class StandardFileSystems {
  public static final String FILE_PROTOCOL = URLUtil.FILE_PROTOCOL;
  public static final String FILE_PROTOCOL_PREFIX = FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR;

  public static final String JAR_PROTOCOL = URLUtil.JAR_PROTOCOL;
  public static final String JAR_PROTOCOL_PREFIX = JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR;

  public static final String JRT_PROTOCOL = URLUtil.JRT_PROTOCOL;
  public static final String JRT_PROTOCOL_PREFIX = JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR;

  private static final Supplier<VirtualFileSystem> ourLocal = CachedSingletonsRegistry.lazy(() -> {
    return VirtualFileManager.getInstance().getFileSystem(URLUtil.FILE_PROTOCOL);
  });

  private static final Supplier<VirtualFileSystem> ourJar = CachedSingletonsRegistry.lazy(() -> {
    return VirtualFileManager.getInstance().getFileSystem(JAR_PROTOCOL);
  });

  public static VirtualFileSystem local() {
    return ourLocal.get();
  }

  public static VirtualFileSystem jar() {
    return ourJar.get();
  }
}