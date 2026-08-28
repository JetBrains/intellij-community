// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock

import com.intellij.openapi.application.CleanupAction
import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.WriteIntentReadActionListener
import com.intellij.openapi.application.WriteLockReacquisitionListener
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** A lock-free [ThreadingSupport] implementation for [MockApplication]. */
internal object MockThreadingSupport : ThreadingSupport {
  override fun <T> runWriteIntentReadAction(computation: () -> T): T = computation()

  override fun tryRunWriteIntentReadAction(action: () -> Unit): Boolean {
    action()
    return true
  }

  override fun isWriteIntentReadAccessAllowed(): Boolean = true

  override fun <T> runReadAction(computation: () -> T): T = computation()

  override fun setAllowanceForReadActions(provider: () -> Boolean) {
  }

  override fun tryRunReadAction(action: () -> Unit): Boolean {
    action()
    return true
  }

  override fun isReadLockedByThisThread(): Boolean = false

  override fun isReadAccessAllowed(): Boolean = true

  override fun <T> runWriteActionBlocking(computation: () -> T): T = computation()

  override suspend fun <T, R> runWriteActionWithExecutor(
    action: () -> T,
    onJobPublished: (Job) -> Unit,
    onJobNotNeeded: (Job) -> Unit,
    shouldProceedWithWriteAction: (() -> Boolean)?,
    executor: (actualAction: () -> T, Job) -> ThreadingSupport.ExecutorResult<R>,
  ): ThreadingSupport.WriteActionResult<R> {
    while (true) {
      val job = Job()
      onJobPublished(job)
      try {
        if (shouldProceedWithWriteAction != null && !shouldProceedWithWriteAction()) {
          return ThreadingSupport.WriteActionResult.Denied
        }
        when (val result = executor(action, job)) {
          is ThreadingSupport.ExecutorResult.Completion -> return ThreadingSupport.WriteActionResult.Completion(result.value)
          ThreadingSupport.ExecutorResult.Retry -> continue
          ThreadingSupport.ExecutorResult.Denied -> return ThreadingSupport.WriteActionResult.Denied
        }
      }
      finally {
        job.cancel()
        onJobNotNeeded(job)
      }
    }
  }

  override fun isWriteActionInProgress(): Boolean = false

  override fun isWriteActionPending(): Boolean = false

  override fun isWriteAccessAllowed(): Boolean = true

  override fun prohibitWriteActionsInside(): CleanupAction = {}

  override fun <T> withLocksProhibited(advice: String, action: () -> T): T = action()

  override fun <T> withLocksSoftlyProhibited(advice: String, logger: (Throwable) -> Unit, action: () -> T): T = action()

  override fun <T> withLockingProhibitionCleared(action: () -> T): T = action()

  override fun getLockingProhibitedAdvice(): String? = null

  override fun parallelizeLock(checkTopmostReadAction: Boolean): Pair<CoroutineContext, CleanupAction> =
    EmptyCoroutineContext to {}

  override fun getLockContextElement(): CoroutineContext = EmptyCoroutineContext

  override fun isParallelizedReadAction(context: CoroutineContext): Boolean = false

  override fun isInTopmostReadAction(): Boolean = false

  override fun runWhenWriteActionIsCompleted(action: () -> Unit) {
    action()
  }

  override fun writeActionFollowupsSize(): Int = 0

  override fun transferWriteActionAndBlock(
    blockingExecutor: (ThreadingSupport.RunnableWithTransferredWriteAction) -> Unit,
    action: Runnable,
  ) {
    blockingExecutor(object : ThreadingSupport.RunnableWithTransferredWriteAction() {
      override fun run() = action.run()
    })
  }

  override fun addWriteActionListener(listener: WriteActionListener) {
  }

  override fun removeWriteActionListener(listener: WriteActionListener) {
  }

  override fun addWriteIntentReadActionListener(listener: WriteIntentReadActionListener) {
  }

  override fun removeWriteIntentReadActionListener(listener: WriteIntentReadActionListener) {
  }

  override fun addReadActionListener(listener: ReadActionListener) {
  }

  override fun removeReadActionListener(listener: ReadActionListener) {
  }

  override fun executeSuspendingWriteAction(action: () -> Unit) {
    action()
  }

  override fun setWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener<*>) {
  }

  override fun removeWriteLockReacquisitionListener(listener: WriteLockReacquisitionListener<*>) {
  }

  override fun hasWriteAction(actionClass: Class<*>): Boolean = false

  override fun dumpSomeDiagnosticInfo(thread: Thread): List<String> = emptyList()
}
