// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface JdkFinder {
  @NotNull
  static JdkFinder getInstance() {
    return ApplicationManager.getApplication().getService(JdkFinder.class);
  }

  /**
   * @return a default install location for JDKs, ETD-friendly
   */
  @Nullable
  String defaultJavaLocation();

  /**
   * Tries to find existing Java SDKs on this computer.
   * If no JDK found, returns possible folders to start file chooser.
   * The method is heavy, it is not recommended to run it from EDT thread.
   * @return suggested sdk home paths (sorted)
   *
   * @deprecated Consider using {@link JdkFinder#suggestHomePaths(Project)}.
   * The JDK should be searched on the machine where the project is located,
   * not where the IDE is running.
   */
  @NotNull
  @Deprecated
  default List<@NotNull String> suggestHomePaths() {
    return suggestHomePaths(null);
  }

  /**
   * Tries to find existing Java SDKs on the computer that contains {@code project}.
   * If no JDK found, returns possible folders to start file chooser.
   * The method is heavy, it is not recommended to run it from EDT thread.
   *
   * @param project the project for which JDK should be suggested, or {@code null} if JDK should be searched locally
   * @return suggested sdk home paths (sorted)
   */
  @NotNull
  List<@NotNull String> suggestHomePaths(@Nullable Project project);
}
