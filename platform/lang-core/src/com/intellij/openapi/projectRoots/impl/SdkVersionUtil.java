// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

/**
 * @author Anna.Kozlova
 */
public final class SdkVersionUtil {
  private SdkVersionUtil() { }

  public static @Nullable JdkVersionDetector.JdkVersionInfo getJdkVersionInfo(@NotNull String homePath) {
    return JdkVersionDetector.getInstance().detectJdkVersionInfo(homePath, AppExecutorUtil.getAppExecutorService());
  }
}
