// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface ProjectTaskListener {
  Topic<ProjectTaskListener> TOPIC = new Topic<>("project task events", ProjectTaskListener.class);

  /**
   * Start tasks execution notification
   */
  default void started(@NotNull ProjectTaskContext context) {}

  /**
   * @param result provides aggregated information about the {@link ProjectTask} execution
   */
  default void finished(@NotNull ProjectTaskManager.Result result) {
    finished(result.getContext(), new ProjectTaskResult(result.isAborted(), result.hasErrors() ? 1 : 0, 0));
  }

  /**
   * @param context         tasks execution context
   * @param executionResult provides aggregated information about the {@link ProjectTask} execution
   * @deprecated use {@link #finished(ProjectTaskManager.Result)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated
  default void finished(@NotNull ProjectTaskContext context, @NotNull ProjectTaskResult executionResult) {}
}
