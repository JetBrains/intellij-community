// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class RunManagerEx extends RunManager {
  public static @NotNull RunManagerEx getInstanceEx(@NotNull Project project) {
    return (RunManagerEx)RunManager.getInstance(project);
  }

  /**
   * @deprecated Use {@link #setSelectedConfiguration(RunnerAndConfigurationSettings)} instead
   */
  @Deprecated(forRemoval = true)
  public final void setActiveConfiguration(@Nullable RunnerAndConfigurationSettings configuration) {
    setSelectedConfiguration(configuration);
  }

  /**
   * @deprecated Use {@link #addConfiguration(RunnerAndConfigurationSettings)}.
   */
  @Deprecated(forRemoval = true)
  public final void addConfiguration(RunnerAndConfigurationSettings settings,
                                     boolean storeInDotIdeaFolder,
                                     List<BeforeRunTask> tasks,
                                     boolean addTemplateTasksIfAbsent) {
    if (storeInDotIdeaFolder) {
      settings.storeInDotIdeaFolder();
    }
    else {
      settings.storeInLocalWorkspace();
    }
    setBeforeRunTasks(settings.getConfiguration(), tasks, addTemplateTasksIfAbsent);
    addConfiguration(settings);
  }

  public abstract @NotNull List<BeforeRunTask> getBeforeRunTasks(@NotNull RunConfiguration configuration);

  public abstract void setBeforeRunTasks(@NotNull RunConfiguration configuration, @NotNull List<BeforeRunTask> tasks);

  /**
   * @deprecated use {@link #setBeforeRunTasks(RunConfiguration, List)}
   */
  @Deprecated
  public final void setBeforeRunTasks(@NotNull RunConfiguration configuration, @NotNull List<BeforeRunTask> tasks, @SuppressWarnings("unused") boolean addEnabledTemplateTasksIfAbsent) {
    setBeforeRunTasks(configuration, tasks);
  }

  public abstract @NotNull <T extends BeforeRunTask<?>> List<@NotNull T> getBeforeRunTasks(@NotNull RunConfiguration settings, Key<T> taskProviderId);

  public abstract @NotNull <T extends BeforeRunTask<?>> List<T> getBeforeRunTasks(Key<T> taskProviderId);

  public Icon getConfigurationIcon(@NotNull RunnerAndConfigurationSettings settings) {
    return getConfigurationIcon(settings, false);
  }

  public abstract @NotNull Icon getConfigurationIcon(@NotNull RunnerAndConfigurationSettings settings, boolean withLiveIndicator);

  /**
   * @deprecated Use {@link #getAllSettings()}
   */
  @Deprecated(forRemoval = true)
  public final @NotNull Collection<RunnerAndConfigurationSettings> getSortedConfigurations() {
    return getAllSettings();
  }

  /**
   * @deprecated Use {@link RunManagerListener#TOPIC} instead.
   */
  @Deprecated(forRemoval = true)
  public void addRunManagerListener(@NotNull RunManagerListener listener) {
  }

  @SafeVarargs
  public static void disableTasks(Project project, RunConfiguration settings, Key<? extends BeforeRunTask> @NotNull ... keys) {
    for (Key<? extends BeforeRunTask> key : keys) {
      List<? extends BeforeRunTask> tasks = getInstanceEx(project).getBeforeRunTasks(settings, key);
      for (BeforeRunTask task : tasks) {
        task.setEnabled(false);
      }
    }
  }

  @SafeVarargs
  public static int getTasksCount(Project project, RunConfiguration settings, Key<? extends BeforeRunTask> @NotNull ... keys) {
    return Arrays.stream(keys).mapToInt(key -> getInstanceEx(project).getBeforeRunTasks(settings, key).size()).sum();
  }
}