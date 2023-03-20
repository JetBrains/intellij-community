// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface ProjectTaskListener {
  @Topic.ProjectLevel
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
  @Deprecated(forRemoval = true)
  default void finished(@NotNull ProjectTaskContext context, @NotNull ProjectTaskResult executionResult) {}
}
