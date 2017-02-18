/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  /** @deprecated use {@link #detectJdkVersion(String)} to be removed in IDEA 2018 */
  @Nullable
  @SuppressWarnings("unused")
  public static String readVersionFromProcessOutput(@NotNull String homePath, @NotNull String[] command, String versionLineMarker) {
    return JdkVersionDetector.getInstance().detectJdkVersion(homePath, ACTION_RUNNER);
  }

  @Nullable
  public static String detectJdkVersion(@NotNull String homePath) {
    return JdkVersionDetector.getInstance().detectJdkVersion(homePath, ACTION_RUNNER);
  }

  @Nullable
  public static JdkVersionDetector.JdkVersionInfo getJdkVersionInfo(@NotNull String homePath) {
    return JdkVersionDetector.getInstance().detectJdkVersionInfo(homePath, ACTION_RUNNER);
  }
}