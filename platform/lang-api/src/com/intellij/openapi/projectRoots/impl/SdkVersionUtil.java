/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

/**
 * @author Anna.Kozlova
 * @since 12-Aug-2006
 */
public class SdkVersionUtil {
  private static final JdkVersionDetector.ActionRunner ACTION_RUNNER = (r) -> ApplicationManager.getApplication().executeOnPooledThread(r);

  private SdkVersionUtil() { }

  /** @deprecated use {@link #getJdkVersionInfo(String)} (to be removed in IDEA 2019) */
  @Nullable
  public static String detectJdkVersion(@NotNull String homePath) {
    return JdkVersionDetector.getInstance().detectJdkVersion(homePath, ACTION_RUNNER);
  }

  @Nullable
  public static JdkVersionDetector.JdkVersionInfo getJdkVersionInfo(@NotNull String homePath) {
    return JdkVersionDetector.getInstance().detectJdkVersionInfo(homePath, ACTION_RUNNER);
  }
}