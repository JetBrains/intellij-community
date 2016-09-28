/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunProfileStarter;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

/**
 * Allows to postpone actual {@link RunProfileState} execution until all the needed preparations are done.
 */
public abstract class AsyncGenericProgramRunner<Settings extends RunnerSettings> extends BaseProgramRunner<Settings> {
  @Override
  protected final void execute(@NotNull final ExecutionEnvironment environment,
                               @Nullable final Callback callback,
                               @NotNull final RunProfileState state) throws ExecutionException {
    prepare(environment, state)
      .done(result -> UIUtil.invokeLaterIfNeeded(() -> {
        if (!environment.getProject().isDisposed()) {
          startRunProfile(environment, state, callback, result);
        }
      }));
  }

  /**
   * Makes all the needed preparations for the further execution. Although this method is called in EDT,
   * these preparations can be performed in a background thread.
   *
   * You must call {@link ExecutionUtil#handleExecutionError} in case of error
   *
   * @param environment ExecutionEnvironment instance
   * @param state RunProfileState instance
   * @return RunProfileStarter async result
   */
  @NotNull
  protected abstract Promise<RunProfileStarter> prepare(@NotNull ExecutionEnvironment environment, @NotNull RunProfileState state) throws ExecutionException;

  private static void startRunProfile(@NotNull ExecutionEnvironment environment,
                                      @NotNull RunProfileState state,
                                      @Nullable final Callback callback,
                                      @Nullable final RunProfileStarter starter) {
    ExecutionManager.getInstance(environment.getProject()).startRunProfile(new RunProfileStarter() {
      @Override
      public RunContentDescriptor execute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        return postProcess(environment, starter == null ? null : starter.execute(state, environment), callback);
      }
    }, state, environment);
  }
}
