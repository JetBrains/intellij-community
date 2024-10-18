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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abstraction layer for executing external system tasks.
 */
public interface ExternalSystemTaskManager<S extends ExternalSystemExecutionSettings> {

  boolean cancelTask(
    @NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException;

  /**
   * @deprecated use {@link ExternalSystemTaskManager#executeTasks(ExternalSystemTaskId, List, String, ExternalSystemExecutionSettings, String, ExternalSystemTaskNotificationListener)}
   */
  @Deprecated
  default void executeTasks(@NotNull ExternalSystemTaskId id,
                            @NotNull List<String> taskNames,
                            @NotNull String projectPath,
                            @Nullable S settings,
                            @NotNull List<String> vmOptions,
                            @NotNull List<String> scriptParameters,
                            @Nullable String jvmParametersSetup,
                            @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
  }

  default void executeTasks(@NotNull ExternalSystemTaskId id,
                            @NotNull List<String> taskNames,
                            @NotNull String projectPath,
                            @Nullable S settings,
                            @Nullable String jvmParametersSetup,
                            @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
    List<String> vmOptions = settings == null ? ContainerUtil.emptyList() : settings.getJvmArguments();
    List<String> arguments = settings == null ? ContainerUtil.emptyList() : settings.getArguments();
    executeTasks(id, taskNames, projectPath, settings, vmOptions, arguments, jvmParametersSetup, listener);
  }

  @SuppressWarnings("rawtypes")
  @ApiStatus.Internal
  class NoOp implements ExternalSystemTaskManager {
    @Override
    public boolean cancelTask(
      @NotNull ExternalSystemTaskId id,
      @NotNull ExternalSystemTaskNotificationListener listener
    ) throws ExternalSystemException {
      // noop
      return false;
    }

    @Override
    public void executeTasks(
      @NotNull ExternalSystemTaskId id,
      @NotNull List taskNames,
      @NotNull String projectPath,
      @Nullable ExternalSystemExecutionSettings settings,
      @Nullable String jvmParametersSetup,
      @NotNull ExternalSystemTaskNotificationListener listener
    ) throws ExternalSystemException {
      // noop
    }
  }
}
