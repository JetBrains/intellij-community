// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExecutorRegistry {
  public static ExecutorRegistry getInstance() {
    return ServiceManager.getService(ExecutorRegistry.class);
  }

  /**
   * @deprecated Use Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  public final Executor @NotNull [] getRegisteredExecutors() {
    // do not return array from EP — to avoid accidental mutation
    return Executor.EXECUTOR_EXTENSION_NAME.getExtensionList().toArray(new Executor[0]);
  }

  @Nullable
  public abstract Executor getExecutorById(@NotNull String executorId);

  /**
   * Consider to use {@link ExecutionManager#isStarting(ExecutionEnvironment)}
   */
  @SuppressWarnings("MethodMayBeStatic")
  public final boolean isStarting(@NotNull Project project, @NotNull String executorId, @NotNull String runnerId) {
    return ExecutionManager.getInstance(project).isStarting(executorId, runnerId);
  }

  @SuppressWarnings("MethodMayBeStatic")
  public final boolean isStarting(@NotNull ExecutionEnvironment environment) {
    return ExecutionManager.getInstance(environment.getProject()).isStarting(environment);
  }
}
