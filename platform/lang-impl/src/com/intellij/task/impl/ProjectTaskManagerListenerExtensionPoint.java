// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ProjectTaskManagerListenerExtensionPoint {
  ExtensionPointName<ProjectTaskManagerListenerExtensionPoint> EP_NAME = ExtensionPointName.create("com.intellij.projectTaskManagerListener");

  void beforeRun(@NotNull Project project, @NotNull ProjectTaskContext context) throws ExecutionException;

  void afterRun(@NotNull Project project, @NotNull ProjectTaskManager.Result result) throws ExecutionException;
}
