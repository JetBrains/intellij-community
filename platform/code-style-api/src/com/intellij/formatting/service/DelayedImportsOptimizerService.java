// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This service is needed to manage moments when optimize imports tasks should be invoked, because sometimes it can be crucial
 */
@ApiStatus.Internal
public interface DelayedImportsOptimizerService {
  static DelayedImportsOptimizerService getInstance(Project project) {
    return project.getService(DelayedImportsOptimizerService.class);
  }

  /**
   * @param task optimize imports task
   * @return {@code true} if the task was delayed successfully,
   * {@code false} otherwise, and a client should manage task lifetime by themselves
   */
  boolean delayOptimizeImportsTask(@NotNull Task task);
}
