// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
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
 * Code blocks running inside should be prepared to get this exception at any moment,
 * and they should call {@link ProgressManager#checkCanceled()} or {@link ProgressIndicator#checkCanceled()} frequently enough.
 * They should also be side-effect-free or at least idempotent, to avoid consistency issues when restarted in the middle.
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
   * @return a copy of this builder that cancels submitted read actions once the specified progress indicator is cancelled.
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> cancelWith(@NotNull ProgressIndicator progressIndicator);

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
   * Merges together similar computations by cancelling the previous ones when a new one is submitted.
   * This can be useful when the results of the previous computation won't make sense anyway in the changed environment.
   * @param equality objects that together identify the computation: if they're all equal in two submissions,
   *                 then the computations are merged. Callers should take care to pass something unique there
   *                 (e.g. some {@link com.intellij.openapi.util.Key} or {@code this} {@code getClass()}),
   *                 so that computations from different places won't interfere.
   * @return a copy of this builder which, when submitted, cancels previously submitted running computations with equal equality objects
   */
  @Contract(pure = true)
  NonBlockingReadAction<T> coalesceBy(@NotNull Object... equality);

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

  /**
   * Run this computation on the current thread in a non-blocking read action, when possible.
   * Note: this method can throw various exceptions (see "Throws" section)
   * and can block the current thread for an indefinite amount of time with just waiting,
   * which can lead to thread starvation or unnecessary thread pool expansion.
   * Besides that, after a read action is finished, a write action in another thread can occur at any time and make the
   * just computed value obsolete.
   * Therefore, it's advised to use asynchronous {@link #submit} API where possible,
   * preferably coupled with {@link #finishOnUiThread} to ensure result validity.<p></p>
   *
   * If the current thread already has read access, the computation is executed as is, without any write-action-cancellability.
   * It's the responsibility of the caller to take care about it.<p></p>
   *
   * {@link #finishOnUiThread} and {@link #coalesceBy} are not supported with synchronous non-blocking read actions.
   *
   * @return the result of the computation
   * @throws ProcessCanceledException if the computation got expired due to {@link #expireWhen} or {@link #expireWith} or {@link #cancelWith}.
   * @throws IllegalStateException if current thread already has read access and the constraints (e.g. {@link #inSmartMode} are not satisfied)
   * @throws RuntimeException when the computation throws an exception. If it's a checked one, it's wrapped into a {@link RuntimeException}.
   */
  T executeSynchronously() throws ProcessCanceledException;
}
