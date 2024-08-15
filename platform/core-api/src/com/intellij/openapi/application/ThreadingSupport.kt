// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.lang.Deprecated
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.swing.JComponent

interface ThreadingSupport {
  @ApiStatus.Internal
  fun postInit(writeThread: Thread) {}

  /**
   * Runs the specified computation in a write intent. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   *
   * See also [WriteIntentReadAction.compute] for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   */
  // @Throws(E::class)
  fun <T, E : Throwable?> runWriteIntentReadAction(computation: ThrowableComputable<T, E>): T


  /**
   * Acquires Write Intent lock if it's not acquired by the current thread.
   *
   * This is low-level API, please use [WriteIntentReadAction].
   *
   * @param invokedClassFqn fully qualified name of the class requiring the write-intent lock.
   * @return `true` if this call acquired lock, `false` if lock was taken already.
   */
  @ApiStatus.Internal
  fun acquireWriteIntentLock(invokedClassFqn: String?): Boolean

  /**
   * Release Write Intent lock acquired with [acquireWriteIntentLock].
   *
   * This is low-level API, please use [WriteIntentReadAction].
   */
  @ApiStatus.Internal
  fun releaseWriteIntentLock()

  /**
   * Checks, is Write Intent lock  acquired by the current thread.
   *
   * This is low-level API, please use [WriteIntentReadAction].
   *
   * @return `true` if lock is acquired, `false` otherwise.
   */
  @ApiStatus.Internal
  fun isWriteIntentLocked(): Boolean

  /**
   * Runs specified action with disabled implicit read lock if this feature is enabled with system property.
   *
   * @see com.intellij.idea.StartupUtil.isImplicitReadOnEDTDisabled
   * @param runnable action to run with disabled implicit read lock.
   */
  @ApiStatus.Internal
  fun runWithoutImplicitRead(runnable: Runnable)

  /**
   * Runs specified action with enabled implicit read lock if this feature is enabled with system property.
   *
   * @see com.intellij.idea.StartupUtil.isImplicitReadOnEDTDisabled
   * @param runnable action to run with enabled implicit read lock.
   */
  @ApiStatus.Internal
  fun runWithImplicitRead(runnable: Runnable)

  /**
   * Requests pooled thread to execute the action.
   *
   * This pool is an
   *   - Unbounded.
   *   - Application-wide, always active, non-shutdownable singleton.
   *
   * You can use this pool for long-running and/or IO-bound tasks.
   *
   * @param action to be executed
   * @return future result
   */
  @RequiresBlockingContext
  fun executeOnPooledThread(action: Runnable, expired: BooleanSupplier): Future<*>

  /**
   * Requests pooled thread to execute the action.
   *
   * This pool is an
   *   - Unbounded.
   *   - Application-wide, always active, non-shutdownable singleton.
   *
   * You can use this pool for long-running and/or IO-bound tasks.
   *
   * @param action to be executed
   * @return future result
   */
  @RequiresBlockingContext
  fun <T> executeOnPooledThread(action: Callable<T>, expired: BooleanSupplier): Future<T>


  /**
   * Runs the specified action under the write-intent lock. Can be called from any thread. The action is executed immediately
   * if no write-intent action is currently running, or blocked until the currently running write-intent action completes.
   *
   * This method is used to implement higher-level API. Please do not use it directly.
   *
   * @param action the action to run
   */
  @ApiStatus.Internal
  fun runIntendedWriteActionOnCurrentThread(action: Runnable)

  /**
   * Runs the specified action, releasing the write-intent lock if it is acquired at the moment of the call.
   *
   * This method is used to implement higher-level API. Please do not use it directly.
   */
  @ApiStatus.Internal
  // @Throws(E::class)
  fun <T, E : Throwable?> runUnlockingIntendedWrite(action: ThrowableComputable<T, E>): T

  /**
   * Set a [ReadActionListener].
   *
   * Only one listener can be set. It is error to set second listener.
   *
   * @param listener the listener to set
   */
  @ApiStatus.Internal
  fun setReadActionListener(listener: ReadActionListener)

  /**
   * Removes a [ReadActionListener].
   *
   * It is error to remove listener which was not set early.
   *
   * @param listener the listener to remove
   */
  @ApiStatus.Internal
  fun removeReadActionListener(listener: ReadActionListener)

  /**
   * Runs the specified read action. Can be called from any thread. The action is executed immediately
   * if no write action is currently running, or blocked until the currently running write action completes.
   *
   * See also [ReadAction.run] for a more lambda-friendly version.
   *
   * @param action the action to run.
   * @see CoroutinesKt.readAction
   *
   * @see CoroutinesKt.readActionBlocking
   */
  @RequiresBlockingContext
  fun runReadAction(action: Runnable)

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   *
   * See also [ReadAction.compute] for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @see CoroutinesKt.readAction
   * @see CoroutinesKt.readActionBlocking
   */
  @RequiresBlockingContext
  fun <T> runReadAction(computation: Computable<T>): T

  /**
   * Runs the specified computation in a read action. Can be called from any thread. The action is executed
   * immediately if no write action is currently running, or blocked until the currently running write action
   * completes.
   *
   * See also [ReadAction.compute] for a more lambda-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   * @throws E re-frown from ThrowableComputable
   * @see CoroutinesKt.readAction
   * @see CoroutinesKt.readActionBlocking
   */
  @RequiresBlockingContext
  // @Throws(E::class)
  fun <T, E : Throwable?> runReadAction(computation: ThrowableComputable<T, E>): T

  /**
   * Tries to acquire the read lock and run the `action`.
   *
   * @return true if action was run while holding the lock, false if was unable to get the lock and action was not run
   */
  fun tryRunReadAction(action: Runnable): Boolean

  /**
   * Check, if read lock is acquired by current thread already.
   *
   * @return `true` if read lock has been acquired, `false` otherwise.
   */
  fun isReadLockedByThisThread(): Boolean

  /**
   * Check, if read access is allowed for current thread.
   *
   * @return `true` if read is allowed, `false` otherwise.
   */
  fun isReadAccessAllowed(): Boolean

  /**
   * Adds a [WriteActionListener].
   *
   * Only one listener can be set. It is error to set second listener.
   *
   * @param listener the listener to set
   */
  fun setWriteActionListener(listener: WriteActionListener)

  /**
   * Removes a [WriteActionListener].
   *
   * It is error to remove listener which was not set early.
   *
   * @param listener the listener to remove
   */
  @ApiStatus.Internal
  fun removeWriteActionListener(listener: WriteActionListener)

  /**
   * Runs the specified write action. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no read actions are currently running, or blocked until all read actions complete.
   *
   * See also [WriteAction.run] for a more lambda-friendly version.
   *
   * @param action the action to run
   * @see WriteAction
   */
  @RequiresBlockingContext
  fun runWriteAction(action: Runnable)

  /**
   * Runs the specified write action. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no read actions are currently running, or blocked until all read actions complete.
   *
   * See also [WriteAction.run] for a more lambda-friendly version.
   *
   * @param computation the action to run
   * @return the result returned by the computation.
   * @see WriteAction
   */
  @RequiresBlockingContext
  fun <T> runWriteAction(computation: Computable<T>): T

  /**
   * Runs the specified write action. Must be called from the Swing dispatch thread. The action is executed
   * immediately if no read actions are currently running, or blocked until all read actions complete.
   *
   * See also [WriteAction.run] for a more lambda-friendly version.
   *
   * @param computation the action to run
   * @return the result returned by the computation.
   * @see WriteAction
   */
  @RequiresBlockingContext
  // @Throws(E::class)
  fun <T, E : Throwable?> runWriteAction(computation: ThrowableComputable<T, E>): T

  /**
   * If called inside a write-action, executes the given code under modal progress with write-lock released (e.g., to allow for read-action
   * parallelization).
   * It's the caller's responsibility to invoke this method only when the model is in an internally consistent state,
   * so that background threads with read actions don't see half-baked PSI/VFS/etc. The runnable may perform write-actions itself;
   * callers should be ready for those.
   */
  fun executeSuspendingWriteAction(project: Project?, title: @NlsContexts.DialogTitle String, runnable: Runnable)

  /**
   * Returns `true` if there is currently executing write action of the specified class.
   *
   * @param actionClass the class of the write action to return.
   * @return `true` if the action is running, or `false` if no action of the specified class is currently executing.
   */
  fun hasWriteAction(actionClass: Class<*>): Boolean

  /**
   * @return true if some thread is performing write action right now.
   * @see runWriteAction
   */
  fun isWriteActionInProgress(): Boolean

  /**
   * @return true if the EDT started to acquire write action but has not acquired it yet.
   * @see runWriteAction
   */
  fun isWriteActionPending(): Boolean

  /**
   * Checks if the write access is currently allowed.
   *
   * @return `true` if the write access is currently allowed, `false` otherwise.
   * @see .assertWriteAccessAllowed
   * @see .runWriteAction
   */
  @Contract(pure = true)
  fun isWriteAccessAllowed(): Boolean


  @ApiStatus.Experimental
  fun runWriteActionWithCancellableProgressInDispatchThread(title: @NlsContexts.ProgressTitle String,
                                                            project: Project?,
                                                            parentComponent: JComponent?,
                                                            action: Consumer<in ProgressIndicator?>): Boolean

  @ApiStatus.Experimental
  fun runWriteActionWithNonCancellableProgressInDispatchThread(title: @NlsContexts.ProgressTitle String,
                                                               project: Project?,
                                                               parentComponent: JComponent?,
                                                               action: Consumer<in ProgressIndicator?>): Boolean

  /**
   * Use [runReadAction] instead
   */
  @Deprecated
  fun acquireReadActionLock(): AccessToken


  /**
   * Use [runWriteAction], [WriteAction.run], or [WriteAction.compute] instead
   */
  @Deprecated
  fun acquireWriteActionLock(marker: Class<*>): AccessToken

  /**
   * Disable write actions till token will be released.
   */
  fun prohibitWriteActionsInside(): AccessToken

  /**
   * DO NOT USE
   */
  @ApiStatus.Internal
  // @Throws(CannotRunReadActionException::class)
  fun executeByImpatientReader(runnable: Runnable)

  /**
   * DO NOT USE
   */
  @ApiStatus.Internal
  fun isInImpatientReader(): Boolean

  /**
   * DO NOT USE
   */
  @ApiStatus.Internal
  fun isInsideUnlockedWriteIntentLock(): Boolean
}