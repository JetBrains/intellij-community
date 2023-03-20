// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * An internal service not supposed to be used directly
 */
@ApiStatus.Internal
public abstract class AsyncExecutionService {

  /**
   * @deprecated use coroutines and their cancellation mechanism instead
   */
  @ScheduledForRemoval
  @Deprecated
  @NotNull
  protected abstract ExpirableExecutor createExecutor(@NotNull Executor executor);

  @NotNull
  protected abstract AppUIExecutor createUIExecutor(@NotNull ModalityState modalityState);

  @NotNull
  protected abstract AppUIExecutor createWriteThreadExecutor(@NotNull ModalityState modalityState);

  @NotNull
  protected abstract <T> NonBlockingReadAction<T> buildNonBlockingReadAction(@NotNull Callable<? extends T> computation);

  @NotNull
  static AsyncExecutionService getService() {
    return ApplicationManager.getApplication().getService(AsyncExecutionService.class);
  }
}
