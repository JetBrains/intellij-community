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
package com.intellij.openapi.externalSystem.service.remote;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public interface RemoteExternalSystemTaskManager<S extends ExternalSystemExecutionSettings> extends RemoteExternalSystemService<S> {

  RemoteExternalSystemTaskManager<ExternalSystemExecutionSettings> NULL_OBJECT = new RemoteExternalSystemTaskManager<>() {
    //@formatter:off
    @Override public void executeTasks(@NotNull ExternalSystemTaskId id, @NotNull List<String> taskNames, @NotNull String projectPath, @Nullable ExternalSystemExecutionSettings settings, @Nullable String jvmParametersSetup) { }
    @Override public boolean cancelTask(@NotNull ExternalSystemTaskId id) { return false; }
    @Override public void setSettings(@NotNull ExternalSystemExecutionSettings settings) { }
    @Override public void setNotificationListener(@NotNull ExternalSystemTaskNotificationListener notificationListener) { }
    @Override public boolean isTaskInProgress(@NotNull ExternalSystemTaskId id) { return false; }
    @Override public @NotNull Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> getTasksInProgress() { return Collections.emptyMap(); }
    //@formatter:on
  };

  void executeTasks(
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> taskNames,
    @NotNull String projectPath,
    @Nullable S settings,
    @Nullable String jvmParametersSetup
  ) throws RemoteException, ExternalSystemException;

  @Override
  boolean cancelTask(
    @NotNull ExternalSystemTaskId id
  ) throws RemoteException, ExternalSystemException;
}
