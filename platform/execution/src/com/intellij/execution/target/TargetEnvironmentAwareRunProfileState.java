// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

@ApiStatus.Experimental
public interface TargetEnvironmentAwareRunProfileState extends RunProfileState {
  void prepareTargetEnvironmentRequest(@NotNull TargetEnvironmentRequest request,
                                       @NotNull TargetProgressIndicator targetProgressIndicator) throws ExecutionException;

  /**
   * @throws ExecutionException to notify that preparation failed, and execution should not be proceeded. Should be localised.
   */
  void handleCreatedTargetEnvironment(@NotNull TargetEnvironment targetEnvironment,
                                      @NotNull TargetProgressIndicator targetProgressIndicator)
    throws ExecutionException;

  default <T> Promise<T> prepareTargetToCommandExecution(@NotNull ExecutionEnvironment env,
                                                         @NotNull Logger logger,
                                                         @NonNls String logFailureMessage,
                                                         @NotNull ThrowableComputable<? extends T, ? extends ExecutionException> afterPreparation)
    throws ExecutionException {
    Promise<Object> preparationTasks;
    final Project project = env.getProject();
    RunProfile runProfile = env.getRunProfile();
    if (((TargetEnvironmentAwareRunProfile)runProfile).needPrepareTarget()) {
      preparationTasks = ExecutionManager.getInstance(project).executePreparationTasks(env, this);
    }
    else {
      preparationTasks = Promises.resolvedPromise();
    }

    return preparationTasks.thenAsync((Object o) -> {
      AsyncPromise<@Nullable T> promise = new AsyncPromise<>();
      ProgressManager.getInstance().run(new Task.Backgroundable(project, ExecutionBundle.message("progress.title.starting.run.configuration", runProfile.getName())) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            promise.setResult(afterPreparation.compute());
          }
          catch (ProcessCanceledException e) {
            promise.setError(ExecutionBundle.message("canceled.starting.run.configuration"));
          }
          catch (ExecutionException t) {
            logger.warn(logFailureMessage, t);
            promise.setError(t);
          }
          catch (Throwable t) {
            logger.error(logFailureMessage, t);
            promise.setError(t);
          }
        }
      });
      return promise;
    });
  }

  default TargetEnvironmentRequest createCustomTargetEnvironmentRequest() {
    return null;
  }
}