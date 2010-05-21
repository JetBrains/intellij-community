/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Manages {@link RunConfiguration}s.
 *
 * @see RunnerRegistry
 * @see ExecutionManager
 */
public abstract class RunManagerEx extends RunManager {
  public static RunManagerEx getInstanceEx(final Project project) {
    return (RunManagerEx)project.getComponent(RunManager.class);
  }

  @Nullable
  public abstract RunnerAndConfigurationSettings getSelectedConfiguration();

  public abstract boolean isTemporary(RunnerAndConfigurationSettings configuration);

  public abstract void setActiveConfiguration(RunnerAndConfigurationSettings configuration);

  public abstract void setSelectedConfiguration(RunnerAndConfigurationSettings configuration);

  public abstract void setTemporaryConfiguration(RunnerAndConfigurationSettings tempConfiguration);

  public abstract RunManagerConfig getConfig();

  @NotNull
  public abstract RunnerAndConfigurationSettings createConfiguration(String name, ConfigurationFactory type);

  public abstract void addConfiguration(RunnerAndConfigurationSettings settings, boolean isShared, Map<Key<? extends BeforeRunTask>, BeforeRunTask> tasks);

  public abstract void addConfiguration(final RunnerAndConfigurationSettings settings, final boolean isShared);

  public abstract boolean isConfigurationShared(RunnerAndConfigurationSettings settings);

  @NotNull
  public abstract <T extends BeforeRunTask> Map<Key<T>, BeforeRunTask> getBeforeRunTasks(RunConfiguration settings);

  @Nullable
  public abstract <T extends BeforeRunTask> T getBeforeRunTask(RunConfiguration settings, Key<T> taskProviderID);

  @NotNull
  public abstract <T extends BeforeRunTask> Collection<T> getBeforeRunTasks(Key<T> taskProviderID, boolean includeOnlyActiveTasks);

  public abstract RunnerAndConfigurationSettings findConfigurationByName(@NotNull final String name);

  public abstract Collection<RunnerAndConfigurationSettings> getSortedConfigurations();

  public abstract void removeConfiguration(RunnerAndConfigurationSettings settings);

  public abstract void addRunManagerListener(RunManagerListener listener);
  public abstract void removeRunManagerListener(RunManagerListener listener);
}