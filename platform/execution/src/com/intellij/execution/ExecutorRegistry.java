// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExecutorRegistry {
  public static ExecutorRegistry getInstance() {
    return ApplicationManager.getApplication().getService(ExecutorRegistry.class);
  }

  /**
   * @deprecated Use Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()
   */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated
  public final Executor @NotNull [] getRegisteredExecutors() {
    // do not return array from EP to avoid accidental mutation
    return Executor.EXECUTOR_EXTENSION_NAME.getExtensionList().toArray(new Executor[0]);
  }

  public abstract @Nullable Executor getExecutorById(@NotNull String executorId);

  /** @deprecated Use {@link ExecutionManager#isStarting(ExecutionEnvironment)} */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated(forRemoval = true)
  public final boolean isStarting(@NotNull Project project, @NotNull String executorId, @NotNull String runnerId) {
    return ExecutionManager.getInstance(project).isStarting("", executorId, runnerId);
  }

  /** @deprecated Use {@link ExecutionManager#isStarting(ExecutionEnvironment)} */
  @SuppressWarnings("MethodMayBeStatic")
  @Deprecated(forRemoval = true)
  public final boolean isStarting(@NotNull ExecutionEnvironment environment) {
    return ExecutionManager.getInstance(environment.getProject()).isStarting(environment);
  }
}
