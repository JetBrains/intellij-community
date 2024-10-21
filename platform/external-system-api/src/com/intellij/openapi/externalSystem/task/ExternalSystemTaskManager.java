/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.task;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abstraction layer for executing external system tasks.
 */
public interface ExternalSystemTaskManager<S extends ExternalSystemExecutionSettings> {

  /**
   * @deprecated use {@link ExternalSystemTaskManager#executeTasks(String, ExternalSystemTaskId, ExternalSystemExecutionSettings, ExternalSystemTaskNotificationListener)} instead
   */
  @Deprecated
  default void executeTasks(
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> taskNames,
    @NotNull String projectPath,
    @Nullable S settings,
    @NotNull List<String> vmOptions,
    @NotNull List<String> scriptParameters,
    @Nullable String jvmParametersSetup,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException { }

  /**
   * @deprecated use {@link ExternalSystemTaskManager#executeTasks(String, ExternalSystemTaskId, ExternalSystemExecutionSettings, ExternalSystemTaskNotificationListener)} instead
   */
  @Deprecated
  default void executeTasks(
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> taskNames,
    @NotNull String projectPath,
    @Nullable S settings,
    @Nullable String jvmParametersSetup,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException {
    assert settings != null;
    settings.setTasks(taskNames);
    settings.setJvmParameters(jvmParametersSetup);
    executeTasks(projectPath, id, settings, listener);
  }

  default void executeTasks(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id,
    @NotNull S settings,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException {
    var taskNames = settings.getTasks();
    var arguments = settings.getArguments();
    var vmOptions = settings.getJvmArguments();
    var jvmParametersSetup = settings.getJvmParameters();
    executeTasks(id, taskNames, projectPath, settings, vmOptions, arguments, jvmParametersSetup, listener);
  }

  boolean cancelTask(
    @NotNull ExternalSystemTaskId id,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException;

  @SuppressWarnings("rawtypes")
  @ApiStatus.Internal
  class NoOp implements ExternalSystemTaskManager {
    //@formatter:off
    @Override public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener) { return false; }
    @Override public void executeTasks(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @Nullable ExternalSystemExecutionSettings settings, @NotNull ExternalSystemTaskNotificationListener listener) { }
    //@formatter:on
  }
}
