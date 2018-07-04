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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abstraction layer for executing external system tasks.
 * 
 * @author Denis Zhdanov
 * @since 3/14/13 5:04 PM
 */
public interface ExternalSystemTaskManager<S extends ExternalSystemExecutionSettings> {

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
                            @Nullable String jvmAgentSetup,
                            @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
  }

  default void executeTasks(@NotNull ExternalSystemTaskId id,
                            @NotNull List<String> taskNames,
                            @NotNull String projectPath,
                            @Nullable S settings,
                            @Nullable String jvmAgentSetup,
                            @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
    List<String> vmOptions = settings == null ? ContainerUtil.emptyList() : ContainerUtil.newArrayList(settings.getVmOptions());
    List<String> arguments = settings == null ? ContainerUtil.emptyList() : ContainerUtil.newArrayList(settings.getArguments());
    executeTasks(id, taskNames, projectPath, settings, vmOptions, arguments, jvmAgentSetup, listener);
  }

  boolean cancelTask(@NotNull ExternalSystemTaskId id,
                  @NotNull ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException;
}
