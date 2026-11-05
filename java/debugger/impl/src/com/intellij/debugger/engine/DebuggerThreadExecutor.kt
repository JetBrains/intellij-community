// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Sends requests from a cancellable read action to the waiting debugger manager thread.
 * Request coroutines belong to the waiting debugger command, independently of the caller's read action.
 */
@ApiStatus.Internal
class DebuggerThreadExecutor private constructor(private val debuggerScope: CoroutineScope) {
  /**
   * Runs [action] in the waiting debugger command without a read lock.
   * Cancellation releases the caller's read lock without waiting for an active request to finish.
   * The action must not schedule and await another command on the same debugger manager thread.
   */
  @RequiresBackgroundThread
  @RequiresReadLock
  fun <T> execute(action: () -> T): T {
    check(!DebuggerManagerThreadImpl.isManagerThread())
    ProgressManager.checkCanceled()
    val request = debuggerScope.async {
      DebuggerManagerThreadImpl.assertIsManagerThread()
      ThreadingAssertions.assertNoReadAccess()
      runCatching(action)
    }
    try {
      return awaitWithCheckCanceled(request).getOrThrow()
    }
    finally {
      request.cancel()
    }
  }

  private class WaitingThreads(val threads: Set<DebuggerManagerThreadImpl>) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<WaitingThreads>
  }

  companion object {
    /**
     * Runs [action] on a worker and handles its requests in the current debugger command.
     * Cancellation follows both the current job and the progress indicator.
     */
    @RequiresReadLockAbsence
    @OptIn(InternalCoroutinesApi::class)
    fun <T> runInBackground(action: (DebuggerThreadExecutor) -> T): T {
      val managerThread = DebuggerManagerThreadImpl.getCurrentThread()
      val callerJob = currentThreadContext()[Job]
      return runBlockingCancellable {
        val waitingThreads = coroutineContext[WaitingThreads]?.threads.orEmpty() + managerThread
        withContext(WaitingThreads(waitingThreads)) {
          val scope = this
          val callerCancellation = callerJob?.invokeOnCompletion(onCancelling = true) {
            if (callerJob.isCancelled) scope.cancel()
          }
          try {
            val executor = DebuggerThreadExecutor(this)
            withContext(Dispatchers.Default) { action(executor) }
          }
          finally {
            callerCancellation?.dispose()
          }
        }
      }
    }

    internal fun checkCanDispatch(managerThread: DebuggerManagerThreadImpl, context: CoroutineContext) {
      check(managerThread !in context[WaitingThreads]?.threads.orEmpty()) {
        "Cannot await another command on the debugger manager thread while it waits for DebuggerThreadExecutor"
      }
    }

    internal suspend fun waitingThreadsContext(): CoroutineContext =
      currentCoroutineContext()[WaitingThreads] ?: EmptyCoroutineContext
  }
}
