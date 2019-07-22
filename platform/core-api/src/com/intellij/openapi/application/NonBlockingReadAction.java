// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A utility for running non-blocking read actions in background thread.
 * "Non-blocking" means to prevent UI freezes, when a write action is about to occur, a read action can be interrupted by a
 * {@link com.intellij.openapi.progress.ProcessCanceledException} and then restarted.
 *
 * @see ReadAction#nonBlocking
 */
public interface NonBlockingReadAction<T> {

  /**
   * @return a copy of this builder that runs read actions only when index is available in the given project.
   * The operation is canceled if the project is closed before either the background computation or {@link #finishOnUiThread} runnable
   * are completed.
   * @see com.intellij.openapi.project.DumbService
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> inSmartMode(@NotNull Project project);

  /**
   * @return a copy of this builder that runs read actions only when all documents are committed.
   * The operation is canceled if the project is closed before either the background computation or {@link #finishOnUiThread} runnable
   * are completed.
   * @see com.intellij.psi.PsiDocumentManager
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> withDocumentsCommitted(@NotNull Project project);

  /**
   * @return a copy of this builder that cancels submitted read actions after they become obsolete.
   * An action is considered obsolete if any of the conditions provided using {@code expireWhen} returns true).
   * The conditions are checked inside a read action, either on a background or on the UI thread.
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> expireWhen(@NotNull BooleanSupplier expireCondition);

  /**
   * @return a copy of this builder that cancels submitted read actions once the specified disposable is disposed.
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> expireWith(@NotNull Disposable parentDisposable);

  /**
   * @return a copy of this builder that completes submitted read actions on UI thread with the given modality state.
   * The read actions are still executed on background thread, but the callbacks on their completion
   * are invoked on UI thread, and no write action is allowed to interfere before that and possibly invalidate the result.
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> finishOnUiThread(@NotNull ModalityState modality, @NotNull Consumer<T> uiThreadAction);

  /**
   * Call this when a newly submitted computation should cancel previous similar ones,
   * as their results now won't make sense anyway in the changed environment.
   * @param identity an object that identifies the computation together with the calling class. For simplest cases,
   *                 a constant string literal or {@code this} can be used to cancel the previously submitted computation.
   *                 If there can be different computations for different objects (e.g. editors) which can be run in parallel, those objects
   *                 can be used as identity. Note that the calling class is paired with the identity object, so
   *                 different computations originating from different classes which have the same identity object won't interfere with each other.
   * @return a copy of this builder which, when submitted, cancels previously submitted running computations with equal identity objects
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> cancelPrevious(@NotNull Object identity);

  /**
   * Submit this computation to be performed in a non-blocking read action on background thread. The returned promise
   * is completed on the same thread (in the same read action), or on UI thread if {@link #finishOnUiThread} has been called.
   *
   * @param backgroundThreadExecutor an executor to actually run the computation. Common examples are
   *                                 {@link com.intellij.util.concurrency.NonUrgentExecutor#getInstance()} or
   *                                 {@link AppExecutorUtil#getAppExecutorService()} or
   *                                 {@link com.intellij.util.concurrency.BoundedTaskExecutor} on top of that.
   */
  CancellablePromise<T> submit(@NotNull Executor backgroundThreadExecutor);
}
