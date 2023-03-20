// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav Soroka
 */
public interface ProjectDataImportListener {
  @Topic.ProjectLevel
  Topic<ProjectDataImportListener> TOPIC = new Topic<>("project data import listener", ProjectDataImportListener.class);

  default void onImportStarted(@Nullable String projectPath) { }

  default void onImportFinished(@Nullable String projectPath) { }

  /**
   * @deprecated use onImportFailed(String, Throwable) to access the cause
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  default void onImportFailed(@Nullable String projectPath) { }

  default void onImportFailed(@Nullable String projectPath, @NotNull Throwable t) {
    onImportFailed(projectPath);
  }

  default void onFinalTasksStarted(@Nullable String projectPath) { }

  default void onFinalTasksFinished(@Nullable String projectPath) { }
}
