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
   * Runs the specified computation synchronously with a _Write_ lock.
   * - If no _Write_, _Write-Intent-Read_, or _Read_ action is currently running, [computation] runs immediately
   * - If a _Write_, _Write-Intent-Read_, or _Read_ action is currently running, this thread gets **blocked** until [computation] can run.
   *
   * See also [WriteAction.compute] for a more java-friendly version.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   */
  @RequiresBlockingContext
  fun <T> runWriteActionBlocking(computation: () -> T): T

  /**
   * Runs the specified computation asynchronously with a _Write_ lock.
   *
   * This function suspends until it is possible to acquire _Write_ lock, and then it starts running [computation] in the current context.
   * There can happen several redispatches before [computation] runs even if it is possible to acquire write lock right away.
   *
   * @param computation the computation to perform.
   * @return the result returned by the computation.
   */
  suspend fun <T> runWriteAction(computation: () -> T): T

  /**
   * This function allows to conditionally execute [computation] under _Write_ lock
   * while atomically checking a condition provided by [shouldProceedWithWriteAction].
   *
   * The function works in the following steps:
   * 1. Acquire _Write-Intent-Read_ lock;
   * 2. Execute [shouldProceedWithWriteAction];
   * 3. If `true`, proceed with [computation] under _Write_ lock which was atomically upgraded from the previously acquired _Write-Intent-Read_;
   * 4. If `false`, return without executing [computation];
   * 5. Release all acquired locks.
   *
   * Normally, write actions are heavy -- they need to terminate all existing _Read_ actions and cancel pending ones.
   * Also, the Platform usually drops caches on write actions.
   * Sometimes it is possible to avoid the execution of _Write_ action, but the decision needs to be taken with a consistent view of the world.
   * This function can be useful when the client is able to take this decision, for example, in `readAndWriteAction` group of functions.
   *
   * @return the result of the [computation] if it was executed, or `null` if [shouldProceedWithWriteAction] returned `false` .
   */
  suspend fun <T : Any> runWriteActionWithCheckInWriteIntent(shouldProceedWithWriteAction: () -> Boolean, computation: () -> T): T?

  /**
   * @return true if some thread is performing _Write_ action right now
   */
  fun isWriteActionInProgress(): Boolean

  /**
   * @return true if someone is blocked or suspended on the acquisition of _Write_ lock
   */
  fun isWriteActionPending(): Boolean

  /**
   * Checks if the current thread runs with _Write_ lock
   */
  @Contract(pure = true)
  fun isWriteAccessAllowed(): Boolean

  /**
   * Disable _Write_ actions on the current thread until [CleanupAction] will be executed.
   */
  @ApiStatus.Internal
  fun prohibitWriteActionsInside(): CleanupAction

  /**
   * Prevents any attempt to use R/W locks on this thread inside [action].
   * An attempt to take a lock results in [LockAccessDisallowed] exception with [advice] message.
   *
   * @throws LockAccessDisallowed on attempt to take a lock inside [action].
   */
  @ApiStatus.Internal
  @Throws(LockAccessDisallowed::class)
  fun prohibitTakingLocksInsideAndRun(action: () -> Unit, advice: String)

  /**
   * If locking is prohibited for this thread (via [prohibitTakingLocksInsideAndRun]),
   * this function will return not-null string with advice on how to fix the problem
   */
  @ApiStatus.Internal
  fun getLockingProhibitedAdvice(): String?

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

  /**
   * If called inside a write-action, executes the given [action] with write-lock released
   * (e.g., to allow for write-intent-read-action parallelization).
   * It's the caller's responsibility to invoke this method only when the model is in an internally consistent state,
   * so that background threads with read actions don't see half-baked PSI/VFS/etc. The runnable may perform write-actions itself;
   * callers should be ready for those.
   */
  @Deprecated("Do not use: this is a severe violation of IJ Platform contracts")
  fun executeSuspendingWriteAction(action: () -> Unit)

  @ApiStatus.Internal
  fun setWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener)

  @ApiStatus.Internal
  fun removeWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener)

  /**
   * Returns `true` if there is a currently executing write action of the specified class.
   *
   * @param actionClass the class of the write action to return.
   * @return `true` if the action is running, or `false` if no action of the specified class is currently executing.
   */
  @Deprecated("Use `ExternalChangeAction` or custom logic for detecting such actions")
  fun hasWriteAction(actionClass: Class<*>): Boolean

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