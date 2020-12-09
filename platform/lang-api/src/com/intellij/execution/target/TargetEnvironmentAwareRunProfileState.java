// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

@ApiStatus.Experimental
public interface TargetEnvironmentAwareRunProfileState extends RunProfileState {
  void prepareTargetEnvironmentRequest(@NotNull TargetEnvironmentRequest request,
                                       @Nullable TargetEnvironmentConfiguration configuration,
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
                                                         @NotNull ThrowableComputable<? extends T, ? extends Throwable> computationForAWT)
    throws ExecutionException {
    ExecutionManager executionManager = ExecutionManager.getInstance(env.getProject());
    return executionManager.executePreparationTasks(env, this).thenAsync((Object o) -> {
      AsyncPromise<T> promise = new AsyncPromise<>();
      ApplicationManager.getApplication().invokeLater(() -> {
        try {
          promise.setResult(computationForAWT.compute());
        }
        catch (ProcessCanceledException e) {
          promise.setError(e.getLocalizedMessage());
        }
        catch (Throwable t) {
          logger.warn(logFailureMessage, t);
          promise.setError(t.getLocalizedMessage());
        }
      });
      return promise;
    });
  }

  default TargetEnvironmentFactory createCustomTargetEnvironmentFactory() {
    return null;
  }

  interface TargetProgressIndicator {
    TargetProgressIndicator EMPTY = new TargetProgressIndicator() {
      @Override
      public void addText(@NotNull String text, @NotNull Key<?> key) { }

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public void stop() { }

      @Override
      public boolean isStopped() {
        return false;
      }
    };

    void addText(@NotNull String text, @NotNull Key<?> key);

    default void addSystemLine(@NotNull String message) {
      addText(message + "\n", ProcessOutputType.SYSTEM);
    }

    boolean isCanceled();

    void stop();

    boolean isStopped();

    default void stopWithErrorMessage(@NotNull String text) {
      addText(text, ProcessOutputType.STDERR);
      addText("\n", ProcessOutputType.STDERR);
      stop();
    }
  }
}