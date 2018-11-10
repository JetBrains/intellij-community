// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class ExecutorRegistry {
  public static ExecutorRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(ExecutorRegistry.class);
  }

  @NotNull
  public abstract Executor[] getRegisteredExecutors();

  public abstract Executor getExecutorById(final String executorId);

  /**
   * Consider to use {@link #isStarting(com.intellij.execution.runners.ExecutionEnvironment)}
   */
  public abstract boolean isStarting(Project project, String executorId, String runnerId);

  public abstract boolean isStarting(@NotNull ExecutionEnvironment environment);
}
