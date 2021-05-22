// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class ExecutionTargetManager {
  public static final Topic<ExecutionTargetListener> TOPIC = Topic.create("ExecutionTarget topic", ExecutionTargetListener.class);

  @NotNull
  public static ExecutionTargetManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExecutionTargetManager.class);
  }

  @NotNull
  public static ExecutionTarget getActiveTarget(@NotNull Project project) {
    return getInstance(project).getActiveTarget();
  }

  public static void setActiveTarget(@NotNull Project project, @NotNull ExecutionTarget target) {
    getInstance(project).setActiveTarget(target);
  }

  @NotNull
  public static List<ExecutionTarget> getTargetsToChooseFor(@NotNull Project project, @Nullable RunConfiguration configuration) {
    List<ExecutionTarget> result = getInstance(project).getTargetsFor(configuration);
    if (result.size() == 1 && DefaultExecutionTarget.INSTANCE.equals(result.get(0))) return Collections.emptyList();
    result = Collections.unmodifiableList(ContainerUtil.filter(result, target -> !target.isExternallyManaged()));
    if (result.size() == 1 && DefaultExecutionTarget.INSTANCE.equals(result.get(0))) {
      return Collections.emptyList();
    }
    return result;
  }

  /**
   * @deprecated use {@link #canRun(RunConfiguration, ExecutionTarget)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static boolean canRun(@Nullable RunnerAndConfigurationSettings settings, @Nullable ExecutionTarget target) {
    return canRun(settings != null ? settings.getConfiguration() : null, target);
  }

  public static boolean canRun(@Nullable RunConfiguration configuration, @Nullable ExecutionTarget target) {
    if (configuration == null || target == null) {
      return false;
    }
    else {
      return getInstance(configuration.getProject()).doCanRun(configuration, target);
    }
  }

  public static boolean canRun(@NotNull ExecutionEnvironment environment) {
    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    return settings != null && canRun(settings.getConfiguration(), environment.getExecutionTarget());
  }

  public abstract boolean doCanRun(@Nullable RunConfiguration configuration, @NotNull ExecutionTarget target);

  public static void update(@NotNull Project project) {
    getInstance(project).update();
  }

  @NotNull
  public abstract ExecutionTarget getActiveTarget();

  public abstract void setActiveTarget(@NotNull ExecutionTarget target);

  @NotNull
  public abstract List<ExecutionTarget> getTargetsFor(@Nullable RunConfiguration configuration);

  /**
   * @deprecated Use {@link #getTargetsFor(RunConfiguration)}
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public List<ExecutionTarget> getTargetsFor(@Nullable RunnerAndConfigurationSettings settings) {
    return getTargetsFor(settings == null ? null : settings.getConfiguration());
  }

  public abstract void update();
}
