// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
interface ThreadingSupport {

  /**
   * Runs the specified computation synchronously with a _Write-Intent-Read_ lock.
   * - If no _Write_ or _Write-Intent-Read_ action is currently running, [computation] runs immediately
   * - If a _Write_ or _Write-Intent-Read_ action is currently running, this thread gets **blocked** until [computation] can run.
   *
   * See also [WriteIntentReadAction.compute] for a more java-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   */
  fun <T> runWriteIntentReadAction(computation: () -> T): T

  /**
   * Runs the specified computation synchronously with a _Write-Intent-Read_ lock.
   * - If no _Write_ or _Write-Intent-Read_ action is currently running, [action] runs immediately, and this method returns `true`
   * - If a _Write_ or _Write-Intent-Read_ action is currently running, [action] does not run, and this method returns `false` immediately.
   *
   * @param action the computation to perform.
   * @return `true` if the action was executed, `false` if another write-intent lock could not be acquired.
   */
  fun tryRunWriteIntentReadAction(action: () -> Unit): Boolean

  /**
   * Checks if the current thread holds _Write-Intent-Read_ or _Write_ lock.
   */
  fun isWriteIntentReadAccessAllowed(): Boolean

  /**
   * Runs the specified computation synchronously with a _Read_ lock.
   * - If no _Write_ action is currently running, [computation] runs immediately
   * - If a _Write_ action is currently running, this thread gets **blocked** until [computation] can run.
   *
   * See also [ReadAction.compute] for a more java-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   */
  @RequiresBlockingContext
  fun <T> runReadAction(computation: () -> T): T

  /**
   * Runs the specified computation synchronously with a _Read_ lock.
   * - If no _Write_ action is currently running, [action] runs immediately, and this method returns `true`
   * - If a _Write_ action is currently running, [action] does not run, and this method returns `false` immediately.
   *
   * @param action the computation to perform.
   * @return `true` if the action was executed, `false` if _Read_ lock could not be acquired.
   */
  fun tryRunReadAction(action: () -> Unit): Boolean

  /**
   * Checks that this thread holds exactly _Read_ lock.
   *
   * @return `true` if this thread holds _Read_ lock, returns `false` if this thread holds _Write_, _Write-Intent-Read_ or no lock.
   */
  fun isReadLockedByThisThread(): Boolean

  /**
   * Checks if the current thread holds _Read_, _Write-Intent-Read_, or _Write_ lock.
   */
  fun isReadAccessAllowed(): Boolean

  /**
   * Adds a [WriteActionListener].
   */
  fun addWriteActionListener(listener: WriteActionListener)

  /**
   * Removes a [WriteActionListener].
   *
   * It is an error to remove a listener that was not added early.
   */
  fun removeWriteActionListener(listener: WriteActionListener)

  /**
   * Adds a [WriteIntentReadActionListener].
   */
  fun addWriteIntentReadActionListener(listener: WriteIntentReadActionListener)

  /**
   * Removes a [WriteIntentReadActionListener].
   *
   * It is an error to remove a listener that was not added early.
   */
  fun removeWriteIntentReadActionListener(listener: WriteIntentReadActionListener)

  /**
   * Add a [ReadActionListener].
   */
  fun addReadActionListener(listener: ReadActionListener)

  /**
   * Removes a [ReadActionListener].
   *
   * It is an error to remove a listener that was not added early.
   */
  fun removeReadActionListener(listener: ReadActionListener)

  @RequiresBlockingContext
  fun <T> runWriteAction(clazz: Class<*>, action: () -> T): T

  /**
   * If called inside a write-action, executes the given [action] with write-lock released
   * (e.g., to allow for read-action parallelization).
   * It's the caller's responsibility to invoke this method only when the model is in an internally consistent state,
   * so that background threads with read actions don't see half-baked PSI/VFS/etc. The runnable may perform write-actions itself;
   * callers should be ready for those.
   */
  fun executeSuspendingWriteAction(action: () -> Unit)

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

  @Deprecated("Use `runReadAction` instead")
  fun acquireReadActionLock(): CleanupAction

  @Deprecated("Use `runWriteAction`, `WriteAction.run`, or `WriteAction.compute` instead")
  fun acquireWriteActionLock(marker: Class<*>): CleanupAction

  /**
   * Disable write actions till token will be released.
   */
  fun prohibitWriteActionsInside(): CleanupAction

  @ApiStatus.Internal
  fun setWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener)

  @ApiStatus.Internal
  fun removeWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener)

  /**
   * Prevents any attempt to use R/W locks inside [action].
   */
  @ApiStatus.Internal
  @Throws(LockAccessDisallowed::class)
  fun prohibitTakingLocksInsideAndRun(action: Runnable, failSoftly: Boolean, advice: String)

  /**
   * Allows using R/W locks inside [action].
   * This is mostly needed for incremental transition from previous approach with unconditional lock acquisiton:
   * we cannot afford prohibiting taking locks for large regions of the platform
   */
  @ApiStatus.Internal
  @Throws(LockAccessDisallowed::class)
  fun allowTakingLocksInsideAndRun(action: Runnable)

  /**
   * If locking is prohibited for this thread (via [prohibitTakingLocksInsideAndRun]),
   * this function will return not-null string with advice on how to fix the problem
   */
  @ApiStatus.Internal
  fun getLockingProhibitedAdvice(): String?

  /** DO NOT USE */
  @ApiStatus.Internal
  fun isInsideUnlockedWriteIntentLock(): Boolean

  @ApiStatus.Internal
  fun getPermitAsContextElement(baseContext: CoroutineContext, shared: Boolean): Pair<CoroutineContext, CleanupAction>

  @ApiStatus.Internal
  fun isParallelizedReadAction(context: CoroutineContext): Boolean

  @ApiStatus.Internal
  fun isInTopmostReadAction(): Boolean

  /**
   * This is a very hacky function ABSOLUTELY NOT FOR PRODUCTION.
   * Consider the following old code:
   * ```kotlin
   * launch(Dispatchers.EDT) {
   *   writeIntentReadAction {
   *     // do something
   *     IndexingTestUtil.waitUntilIndexesAreReady()
   *     // do something else
   *   }
   * }
   *
   * launch(Dispatchers.Default) {
   *   backgroundWriteAction {}
   * }
   * ```
   *
   * This is a deadlock, because `waitUntilIndexesAreReady` spins the event queue inside, and it waits for some write action to happen.
   * When WA is executed on background, the code above would result in a deadlock, because the code in WI waits for (lower-level) Write to finish.
   *
   * This function is a TEMPORARY fix for tests. When we are ready (i.e., when we eliminate write action by default), this hack will be removed.
   */
  @ApiStatus.Internal
  @TestOnly
  fun <T> releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(action: () -> T): T = action()

  class LockAccessDisallowed(override val message: String) : IllegalStateException(message)

  /**
   * Defers [action] while write action is pending or in progress.
   * [action] is guaranteed to run. It may run immediately on the current thread or after some time on an unspecified thread.
   */
  fun runWhenWriteActionIsCompleted(action: () -> Unit)

  /**
   * Executes [action] with [blockingExecutor], and transfers write access to [action].
   * This function requires the acquired write lock.
   *
   * [blockingExecutor] must block the running thread until [action] finishes.
   * [blockingExecutor] can treat the passed runnable in a special way, so we wrap the runnable with [RunnableWithTransferredWriteAction]
   *
   * A typical example of [blockingExecutor] is [javax.swing.SwingUtilities.invokeAndWait]
   */
  @RequiresWriteLock
  fun transferWriteActionAndBlock(blockingExecutor: (RunnableWithTransferredWriteAction) -> Unit, action: Runnable)

  /**
   * This function allows to conditionally execute [action] under write lock while checking a condition provided by [shouldProceedWithWriteAction].
   *
   * The function works in the following steps:
   * 1. Acquire write-intent lock;
   * 2. Execute [shouldProceedWithWriteAction];
   * 3. If true, proceed with [action] under write lock which was atomically upgraded from the previously acquired write-intent;
   * 4. If false, return without executing [action];
   * 5. Release all acquired locks.
   *
   * Normally, write actions are heavy -- they need to terminate all existing read actions and cancel pending ones.
   * Sometimes it is possible to avoid the execution of write action, but the decision needs to be taken with a consistent worldview.
   * This function can be useful when the client is able to take this decision, for example, in `readAndWriteAction` group of functions
   */
  @ApiStatus.Internal
  suspend fun <T : Any> runWriteActionWithCheckInWriteIntent(shouldProceedWithWriteAction: () -> Boolean, action: () -> T): T?

  /**
   * Executes write action while suspending for lock acquisition.
   */
  suspend fun <T> runWriteAction(action: () -> T): T

  /**
   * A marker class that helps others to identify that the runnable needs to run quickly
   */
  abstract class RunnableWithTransferredWriteAction : Runnable {
    companion object {
      const val NAME: String = "RunnableWithTransferredWriteAction"
    }

    override fun toString(): String {
      return NAME
    }
  }
}

typealias CleanupAction = () -> Unit