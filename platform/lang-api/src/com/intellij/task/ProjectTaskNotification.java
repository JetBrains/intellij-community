// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public interface ProjectTaskNotification {
  /**
   * @param executionResult provides aggregated information about the {@link ProjectTask} execution
   * @deprecated use {@link #finished(ProjectTaskContext, ProjectTaskResult)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  default void finished(@NotNull ProjectTaskResult executionResult) {}

  /**
   * @param context         tasks execution context
   * @param executionResult provides aggregated information about the {@link ProjectTask} execution
   */
  default void finished(@NotNull ProjectTaskContext context, @NotNull ProjectTaskResult executionResult) { finished(executionResult); }
}
