// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A utility for running non-blocking read actions in a background thread.
 * <p>
 * <i>Non-blocking</i> means that to prevent UI freezes, when a write action is about to occur, a read action can be interrupted by a
 * {@link ProcessCanceledException} and then restarted.
 * Code running inside such a non-blocking read action should be prepared to get this exception at any moment, and:
 * <ul>
 *   <li>call {@link ProgressManager#checkCanceled()} or {@link ProgressIndicator#checkCanceled()} frequently enough (because our cancellation mechanism is cooperative).</li>
 *   <li>be side-effect-free or at least idempotent, to avoid consistency issues when interrupted in the middle and restarted.</li>
 * </ul>
 * <p>
 * The recommended usage is {@code ReadAction.nonBlocking(...).withXxx()....finishOnUiThread(...).submit(...)}.
 * It's the only way that allows accessing the computation result safely. The alternatives
 * (e.g. {@link #executeSynchronously()}, {@link org.jetbrains.concurrency.Promise} methods) mean that you might get the computation result
 * in a background thread after a read action is finished, so a write action can then occur at any time and make the result outdated.
 * <p/>
 * In a coroutine, use {@link CoroutinesKt#readAction} instead.
 *
 * @see ReadAction#nonBlocking
 */
public interface NonBlockingReadAction<T> {

  /**
   * @return a copy of this builder that runs read actions only when index is available in the given project.
   * The operation is canceled if the project is closed before either the background computation or {@link #finishOnUiThread} runnable
   * are completed.
   * @see com.intellij.openapi.project.DumbService
   * @see CoroutinesKt#smartReadAction
   * @see ReadConstraint.Companion#inSmartMode
   */
  @Contract(pure = true)
  @NotNull
  NonBlockingReadAction<T> inSmartMode(@NotNull Project project);

  /**
   * @return a copy of this builder that runs read actions only when all documents are committed.
   * The operation is canceled if the project is closed before either the background computation or {@link #finishOnUiThread} runnable
   * are completed.
   * @see com.intellij.psi.PsiDocumentManager
   * @see CoroutinesKt#constrainedReadAction
   * @see ReadConstraint.Companion#withDocumentsCommitted
   */
  @Contract(pure = true)
  @NotNull
  NonBlockingReadAction<T> withDocumentsCommitted(@NotNull Project project);

  /**
   * Returns a copy of this builder that cancels submitted read actions after they become obsolete.
   * An action is considered obsolete if any of the conditions provided using {@code expireWhen} returns {@code true}).
   * <p></p>
   * The conditions are checked when read access is allowed, before the computation on a background thread
   * and before {@link #finishOnUiThread} handler, if any.
   * <p></p>
   * Even if the expiration condition becomes {@code true} without a write action during the background computation,
   * it won't be checked until the computation is complete.
   * Hence if you want to cancel the computation immediately, you should handle that separately
   * (e.g. by putting {@link CancellablePromise#cancel()} inside some listener).
   */
  @Contract(pure = true)
  @NotNull
  NonBlockingReadAction<T> expireWhen(@NotNull BooleanSupplier expireCondition);

  /**
   * @return a copy of this builder that synchronizes the specified progress indicator with the inner one created by {@link NonBlockingReadAction}.
   * This means that submitted read actions are cancelled once the outer indicator is cancelled,
   * and the visual changes (e.g. {@link ProgressIndicator#setText}) are propagated from the inner to the outer indicator.
   */
  @Contract(pure = true)
  @NotNull
  NonBlockingReadAction<T> wrapProgress(@NotNull ProgressIndicator progressIndicator);

  /**
   * @return a copy of this builder that cancels submitted read actions once the specified disposable is disposed.
   * In that case the corresponding {@link CancellablePromise} will be canceled immediately, its completion handlers will be called.
   * Currently running background computations will throw a {@link ProcessCanceledException} on the next {@code checkCanceled} call,
   * and if computations or {@link #finishOnUiThread} handlers are scheduled, they won't be executed.
   */
  @Contract(pure = true)
  @NotNull
  NonBlockingReadAction<T> expireWith(@NotNull Disposable parentDisposable);

  /**
   * @return a copy of this builder that completes submitted read actions on UI thread with the given modality state.
   * The read actions are still executed on background thread, but the callbacks on their completion
   * are invoked on UI thread, and no write action is allowed to interfere before that and possibly invalidate the result.
   */
  @Contract(pure = true)
  @NotNull
  NonBlockingReadAction<T> finishOnUiThread(@NotNull ModalityState modality, @NotNull Consumer<? super T> uiThreadAction);

  /**
   * Merges together similar computations by cancelling the previous ones when a new one is submitted.
   * This can be useful when the results of the previous computation won't make sense anyway in the changed environment.
   * NOTE: current implementation prohibit from using same .coalesceBy key for computations of different origins (see
   * {@link com.intellij.openapi.application.impl.NonBlockingReadActionImpl.Submission#getComputationOrigin()} for details).
   *
   * @param equality objects that together identify the computation: if they're all equal in two submissions,
   *                 then the computations are merged. Callers should take care to pass something unique there
   *                 (e.g. some {@link com.intellij.openapi.util.Key} or {@code this}.{@code getClass()}),
   *                 so that computations from different places won't interfere.
   * @return a copy of this builder which, when submitted, cancels previously submitted running computations with equal equality objects
   */
  @Contract(pure = true)
  @NotNull
  NonBlockingReadAction<T> coalesceBy(@NotNull Object @NotNull ... equality);

  /**
   * Submit this computation to be performed in a non-blocking read action on background thread. The returned promise
   * is completed on the same thread (in the same read action), or on UI thread if {@link #finishOnUiThread} has been called.
   *
   * @param backgroundThreadExecutor an executor to actually run the computation. Common examples are
   *                                 {@link com.intellij.util.concurrency.NonUrgentExecutor#getInstance()} or
   *                                 {@link AppExecutorUtil#getAppExecutorService()} or
   *                                 {@link com.intellij.util.concurrency.BoundedTaskExecutor} on top of that.
   */
  @RequiresBlockingContext
  @NotNull
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
   * <p>
   * If the current thread already has read access, the computation is executed as is, without any write-action-cancellability.
   * It's the responsibility of the caller to take care about it.<p></p>
   * <p>
   * {@link #finishOnUiThread} and {@link #coalesceBy} are not supported with synchronous non-blocking read actions.
   *
   * @return the result of the computation
   * @throws ProcessCanceledException if the computation got expired due to {@link #expireWhen} or {@link #expireWith} or {@link #wrapProgress}.
   * @throws IllegalStateException    if current thread already has read access and the constraints (e.g. {@link #inSmartMode} are not satisfied)
   * @throws RuntimeException         when the computation throws an exception. If it's a checked one, it's wrapped into a {@link RuntimeException}.
   */
  @RequiresBlockingContext
  T executeSynchronously() throws ProcessCanceledException;
}
