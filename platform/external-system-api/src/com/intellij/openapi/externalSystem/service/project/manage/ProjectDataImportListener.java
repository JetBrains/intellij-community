// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav Soroka
 */
public interface ProjectDataImportListener {
  @Topic.ProjectLevel
  Topic<ProjectDataImportListener> TOPIC = new Topic<>("project data import listener", ProjectDataImportListener.class);

  void onImportFinished(@Nullable String projectPath);

  @ApiStatus.Experimental
  default void onImportFailed(@Nullable String projectPath) {}
}
