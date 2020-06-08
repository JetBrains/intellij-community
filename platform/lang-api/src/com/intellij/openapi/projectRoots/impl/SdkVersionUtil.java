// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

/**
 * @author Anna.Kozlova
 */
public final class SdkVersionUtil {
  private SdkVersionUtil() { }

  /** @deprecated use {@link #getJdkVersionInfo(String)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Nullable
  public static String detectJdkVersion(@NotNull String homePath) {
    JdkVersionDetector.JdkVersionInfo info = getJdkVersionInfo(homePath);
    return info != null ? JdkVersionDetector.formatVersionString(info.version) : null;
  }

  @Nullable
  public static JdkVersionDetector.JdkVersionInfo getJdkVersionInfo(@NotNull String homePath) {
    return JdkVersionDetector.getInstance().detectJdkVersionInfo(homePath, AppExecutorUtil.getAppExecutorService());
  }
}