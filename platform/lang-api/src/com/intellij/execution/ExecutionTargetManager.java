/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
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
  public static List<ExecutionTarget> getTargetsFor(@NotNull Project project, @Nullable RunnerAndConfigurationSettings settings) {
    return getInstance(project).getTargetsFor(settings);
  }

  @NotNull
  public static List<ExecutionTarget> getTargetsToChooseFor(@NotNull Project project, @Nullable RunnerAndConfigurationSettings settings) {
    List<ExecutionTarget> result = getInstance(project).getTargetsFor(settings);
    if (result.size() == 1 && DefaultExecutionTarget.INSTANCE.equals(result.get(0))) return Collections.emptyList();
    return result;
  }

  public static boolean canRun(@Nullable RunnerAndConfigurationSettings settings, @Nullable ExecutionTarget target) {
    return settings != null && target != null && settings.canRunOn(target) && target.canRun(settings);
  }

  public static boolean canRun(@NotNull ExecutionEnvironment environment) {
    return canRun(environment.getRunnerAndConfigurationSettings(), environment.getExecutionTarget());
  }

  public static void update(@NotNull Project project) {
    getInstance(project).update();
  }

  @NotNull
  public abstract ExecutionTarget getActiveTarget();

  public abstract void setActiveTarget(@NotNull ExecutionTarget target);

  @NotNull
  public abstract List<ExecutionTarget> getTargetsFor(@Nullable RunnerAndConfigurationSettings settings);

  public abstract void update();
}
