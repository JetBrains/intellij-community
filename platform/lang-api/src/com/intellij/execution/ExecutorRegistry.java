// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class ExecutorRegistry {
  public static ExecutorRegistry getInstance() {
    return ServiceManager.getService(ExecutorRegistry.class);
  }

  @NotNull
  public abstract Executor[] getRegisteredExecutors();

  public abstract Executor getExecutorById(@NotNull String executorId);

  /**
   * Consider to use {@link #isStarting(ExecutionEnvironment)}
   */
  public abstract boolean isStarting(@NotNull Project project, @NotNull String executorId, @NotNull String runnerId);

  public final boolean isStarting(@NotNull ExecutionEnvironment environment) {
    return isStarting(environment.getProject(), environment.getExecutor().getId(), environment.getRunner().getRunnerId());
  }
}
