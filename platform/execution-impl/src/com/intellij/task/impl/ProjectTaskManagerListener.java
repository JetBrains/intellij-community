// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use ProjectTaskManagerListenerExtensionPoint
 */
@Deprecated
@ApiStatus.Experimental
public interface ProjectTaskManagerListener {
  void beforeRun(@NotNull ProjectTaskContext context) throws ExecutionException;

  void afterRun(@NotNull ProjectTaskManager.Result result) throws ExecutionException;
}
