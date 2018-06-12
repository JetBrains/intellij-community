// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * An utility for running non-blocking read actions in background thread.
 * "Non-blocking" means to prevent UI freezes, when a write action is about to occur, a read action can be interrupted by a
 * {@link com.intellij.openapi.progress.ProcessCanceledException} and then restarted.
 */
public interface NonBlockingReadAction<T> {

  /** 
   * @return a copy of this builder that runs read actions only when index is available.
   * @see com.intellij.openapi.project.DumbService
   */
  @Contract(pure=true)
  NonBlockingReadAction<T> inSmartMode(@NotNull Project project);

  /**
   * @return a copy of this builder that cancels submitted read actions after they become obsolete (i.e. when the provided condition returns true). If {@code expireWhen} is called several times, any of the corresponding conditions being {@code true} is sufficient for cancelling
   * the activity. The conditions are checked inside a read action, either on background or on UI thread.
   */
  @Contract(pure=true)
  NonBlockingReadAction<T> expireWhen(@NotNull BooleanSupplier expireCondition);

  /**
   * @return a copy of this builder that completes submitted read actions on UI thread with the given modality state.
   * The read actions are still executed on background thread, but the callbacks on their completion
   * are invoked on UI thread, and no write action is allowed to interfere before that and possibly invalidate the result.
   */
  @Contract(pure=true)
  NonBlockingReadAction<T> finishOnUiThread(@NotNull ModalityState modality, @NotNull Consumer<T> uiThreadAction);

  /**
   * Submit this computation to be performed in a non-blocking read action on background thread. The returned promise
   * is completed on the same thread (in the same read action), or on UI thread if {@link #finishOnUiThread} has been called.
   * @param backgroundThreadExecutor an executor to actually run the computation. Common examples are
   *                                 {@link AppExecutorUtil#getAppExecutorService()} or
   *                                 {@link com.intellij.util.concurrency.BoundedTaskExecutor} on top of that.
   */
  CancellablePromise<T> submit(@NotNull Executor backgroundThreadExecutor);

}
