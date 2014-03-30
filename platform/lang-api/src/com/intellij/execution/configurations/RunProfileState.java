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
 * @see RunConfiguration#getState(com.intellij.execution.Executor, com.intellij.execution.runners.ExecutionEnvironment)
 * @see com.intellij.execution.configuration.EmptyRunProfileState
 */
@SuppressWarnings("JavadocReference")
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
  ExecutionResult execute(final Executor executor, @NotNull ProgramRunner runner) throws ExecutionException;
}
