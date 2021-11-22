// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ProgramRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a process which is ready to be started. Normally, a RunProfileState contains an initialized command line, set of environment
 * variables, working directory etc.
 *
 * @see CommandLineState
 * @see RunConfiguration#getState(Executor, com.intellij.execution.runners.ExecutionEnvironment)
 * @see com.intellij.execution.configuration.EmptyRunProfileState
 */
public interface RunProfileState {
  /**
   * Starts the process.
   *
   * @param executor the executor used to start up the process.
   * @param runner   the program runner used to start up the process.
   * @return the result (normally an instance of {@link com.intellij.execution.DefaultExecutionResult}), containing a process handler
   * and a console attached to it.
   *
   * @throws ExecutionException if the execution has failed.
   */
  @Nullable
  ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException;
}
