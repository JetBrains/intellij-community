// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

@OptIn(InternalCoroutinesApi::class)
internal class DispatchedRunnable(job: Job, runnable: Runnable) : ContextAwareRunnable, CompletionHandler {

  private companion object {

    @JvmStatic
    private val runnableUpdater: AtomicReferenceFieldUpdater<DispatchedRunnable, Runnable> = AtomicReferenceFieldUpdater.newUpdater(
      DispatchedRunnable::class.java,
      Runnable::class.java,
      "_runnable",
    )
  }

  @Volatile
  private var _runnable: Runnable? = runnable

  @Volatile
  private var _completionHandle: DisposableHandle? = job.invokeOnCompletion(onCancelling = true, handler = this)

  override fun run() {
    // Clear the reference to avoid scheduling the runnable again when cancelled.
    val runnable = runnableUpdater.getAndSet(this, null)
    if (runnable == null) {
      // Cleared in `invoke`.
      // This code path means that the coroutine was cancelled externally after being scheduled.
    }
    else {
      checkNotNull(_completionHandle).dispose()
      _completionHandle = null
      runnable.run()
    }
  }

  override fun invoke(cause: Throwable?) {
    if (cause == null) {
      // Sanity check: completed means the `run` was executed => the reference must be cleared.
      check(_runnable == null)
      return
    }
    // Clear the reference so this runnable does nothing in `run`.
    val runnable = runnableUpdater.getAndSet(this, null)
    if (runnable == null) {
      // Cleared in `run`.
      // This code path means that the cancellation happened concurrently with `run`,
      // or that the coroutine threw CancellationException manually.
    }
    else {
      // Reschedule the original runnable ignoring the modality state
      // to give the cancelled coroutine a chance to clean its resources and complete.
      ApplicationManagerEx.getApplicationEx().dispatchCoroutineOnEDT(
        ContextAwareRunnable(runnable::run), ModalityState.any()
      )
    }
  }
}
