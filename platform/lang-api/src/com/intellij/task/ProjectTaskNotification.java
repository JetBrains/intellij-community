// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @deprecated replaced in with promises of {@link ProjectTaskManager.Result} and {@link ProjectTaskRunner.Result}
 */
@Deprecated(forRemoval = true)
public interface ProjectTaskNotification {
  /**
   * @param executionResult provides aggregated information about the {@link ProjectTask} execution
   */
  default void finished(@NotNull ProjectTaskResult executionResult) {}

  /**
   * @param context         tasks execution context
   * @param executionResult provides aggregated information about the {@link ProjectTask} execution
   */
  default void finished(@NotNull ProjectTaskContext context, @NotNull ProjectTaskResult executionResult) { finished(executionResult); }
}
