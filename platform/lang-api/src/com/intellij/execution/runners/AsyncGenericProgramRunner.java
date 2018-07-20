// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunProfileStarter;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import static com.intellij.execution.runners.GenericProgramRunnerKt.startRunProfile;

/**
 * @deprecated Use {@link AsyncProgramRunner} instead
 */
@Deprecated
public abstract class AsyncGenericProgramRunner<Settings extends RunnerSettings> extends BaseProgramRunner<Settings> {
  @Override
  protected final void execute(@NotNull ExecutionEnvironment environment,
                               @Nullable Callback callback,
                               @NotNull RunProfileState state) throws ExecutionException {
    prepare(environment, state)
      .onSuccess(result -> UIUtil.invokeLaterIfNeeded(() -> {
        if (!environment.getProject().isDisposed()) {
          startRunProfile(environment, state, callback, result);
        }
      }));
  }

  /**
   * Makes all the needed preparations for the further execution. Although this method is called in EDT,
   * these preparations can be performed in a background thread.
   * Please note that {@link RunProfileState#execute(Executor, ProgramRunner)} should not be called during the preparations
   * to not execute the run profile before "Before launch" tasks.
   *
   * You must call {@link ExecutionUtil#handleExecutionError} in case of error
   *
   * @param environment ExecutionEnvironment instance
   * @param state RunProfileState instance
   * @return RunProfileStarter async result
   */
  @NotNull
  protected abstract Promise<RunProfileStarter> prepare(@NotNull ExecutionEnvironment environment, @NotNull RunProfileState state) throws ExecutionException;
}
