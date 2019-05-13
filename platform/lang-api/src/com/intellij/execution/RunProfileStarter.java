// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

/**
 * Internal use only. Please use {@link com.intellij.execution.runners.GenericProgramRunner} or {@link com.intellij.execution.runners.AsyncProgramRunner}.
 *
 * The callback used to execute a process from the {@link ExecutionManager#startRunProfile(RunProfileStarter, com.intellij.execution.configurations.RunProfileState, com.intellij.execution.runners.ExecutionEnvironment)}*
 * @author nik
 */
public abstract class RunProfileStarter {
  @Nullable
  @Deprecated
  public RunContentDescriptor execute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    throw new AbstractMethodError();
  }

  /**
   * You should NOT throw exceptions in this method.
   * Instead return {@link org.jetbrains.concurrency.Promises#rejectedPromise(Throwable)} or call {@link org.jetbrains.concurrency.AsyncPromise#setError(Throwable)}
   */
  public Promise<RunContentDescriptor> executeAsync(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return Promises.resolvedPromise(execute(state, environment));
  }
}