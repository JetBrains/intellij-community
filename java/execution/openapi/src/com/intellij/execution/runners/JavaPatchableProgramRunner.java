// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public abstract class JavaPatchableProgramRunner<Settings extends RunnerSettings> extends AsyncProgramRunner<Settings> {
  public abstract void patch(JavaParameters javaParameters, RunnerSettings settings, RunProfile runProfile, final boolean beforeExecution)
    throws ExecutionException;

  protected static void runCustomPatchers(JavaParameters javaParameters, Executor executor, RunProfile runProfile) {
    if (runProfile != null) {
      JavaProgramPatcher.EP_NAME.forEachExtensionSafe(patcher -> {
        patcher.patchJavaParameters(executor, runProfile, javaParameters);
      });
    }
  }

  @NotNull
  @Override
  protected final Promise<RunContentDescriptor> execute(@NotNull ExecutionEnvironment environment, @NotNull RunProfileState state)
    throws ExecutionException {
    return Promises.resolvedPromise(doExecute(state, environment));
  }

  protected abstract RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException;
}
