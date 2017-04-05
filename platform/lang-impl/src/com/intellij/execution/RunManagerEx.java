/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class RunManagerEx extends RunManager {
  public static RunManagerEx getInstanceEx(final Project project) {
    return (RunManagerEx)project.getComponent(RunManager.class);
  }

  /**
   * @deprecated use {@link #setSelectedConfiguration(RunnerAndConfigurationSettings)} instead
   */
  @Deprecated
  public void setActiveConfiguration(@Nullable RunnerAndConfigurationSettings configuration) {
    setSelectedConfiguration(configuration);
  }

  public abstract void setTemporaryConfiguration(@Nullable RunnerAndConfigurationSettings tempConfiguration);

  @NotNull
  public abstract RunManagerConfig getConfig();

  public void addConfiguration(@NotNull RunnerAndConfigurationSettings settings) {
    addConfiguration(settings, isConfigurationShared(settings), null, false);
  }

  public void addConfiguration(@NotNull RunnerAndConfigurationSettings settings, boolean isShared) {
    addConfiguration(settings, isShared, null, false);
  }

  public abstract void addConfiguration(RunnerAndConfigurationSettings settings,
                                        boolean isShared,
                                        List<BeforeRunTask> tasks,
                                        boolean addTemplateTasksIfAbsent);

  public abstract boolean isConfigurationShared(RunnerAndConfigurationSettings settings);

  @NotNull
  public abstract List<BeforeRunTask> getBeforeRunTasks(@NotNull RunConfiguration configuration);

  public void setBeforeRunTasks(@NotNull RunConfiguration configuration, @NotNull List<BeforeRunTask> tasks) {
    setBeforeRunTasks(configuration, tasks, false);
  }

  public abstract void setBeforeRunTasks(@NotNull RunConfiguration configuration, @NotNull List<BeforeRunTask> tasks, boolean addEnabledTemplateTasksIfAbsent);

  @NotNull
  public abstract <T extends BeforeRunTask> List<T> getBeforeRunTasks(@NotNull RunConfiguration settings, Key<T> taskProviderId);

  @NotNull
  public abstract <T extends BeforeRunTask> List<T> getBeforeRunTasks(Key<T> taskProviderId);

  public abstract RunnerAndConfigurationSettings findConfigurationByName(@Nullable String name);

  public Icon getConfigurationIcon(@NotNull RunnerAndConfigurationSettings settings) {
    return getConfigurationIcon(settings, false);
  }

  public abstract Icon getConfigurationIcon(@NotNull RunnerAndConfigurationSettings settings, boolean withLiveIndicator);

  @NotNull
  public abstract Collection<RunnerAndConfigurationSettings> getSortedConfigurations();

  public abstract void removeConfiguration(@Nullable RunnerAndConfigurationSettings settings);

  public abstract void addRunManagerListener(RunManagerListener listener);
  public abstract void removeRunManagerListener(RunManagerListener listener);

  @NotNull
  public abstract Map<String, List<RunnerAndConfigurationSettings>> getStructure(@NotNull ConfigurationType type);

  @SafeVarargs
  public static void disableTasks(Project project, RunConfiguration settings, @NotNull Key<? extends BeforeRunTask>... keys) {
    for (Key<? extends BeforeRunTask> key : keys) {
      List<? extends BeforeRunTask> tasks = getInstanceEx(project).getBeforeRunTasks(settings, key);
      for (BeforeRunTask task : tasks) {
        task.setEnabled(false);
      }
    }
  }

  @SafeVarargs
  public static int getTasksCount(Project project, RunConfiguration settings, @NotNull Key<? extends BeforeRunTask>... keys) {
    return Arrays.stream(keys).mapToInt(key -> getInstanceEx(project).getBeforeRunTasks(settings, key).size()).sum();
  }
}