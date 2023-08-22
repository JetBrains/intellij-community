// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

/**
 * Internal use only. Please use {@link com.intellij.execution.runners.GenericProgramRunner} or {@link com.intellij.execution.runners.AsyncProgramRunner}.
 * <p>
 * The callback used to execute a process from the {@link ExecutionManager#startRunProfile(RunProfileStarter, ExecutionEnvironment)}.
 */
@ApiStatus.Internal
public abstract class RunProfileStarter {
  /**
   * @deprecated use {@link #executeAsync(ExecutionEnvironment)}
   */
  @SuppressWarnings("unused")
  @Nullable
  @Deprecated(forRemoval = true)
  public RunContentDescriptor execute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    throw new AbstractMethodError();
  }

  /**
   * You should NOT throw exceptions in this method.
   * Instead return {@link Promises#rejectedPromise(Throwable)} or call {@link org.jetbrains.concurrency.AsyncPromise#setError(Throwable)}
   */
  @NotNull
  public Promise<RunContentDescriptor> executeAsync(@NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    RunProfileState state = environment.getState();
    return Promises.resolvedPromise(state == null ? null : execute(state, environment));
  }
}