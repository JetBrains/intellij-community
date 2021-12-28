// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicReference

internal class DispatchedRunnable(job: Job, runnable: Runnable) : Runnable {

  private val runnableRef: AtomicReference<Runnable> = AtomicReference(runnable)

  init {
    @Suppress("EXPERIMENTAL_API_USAGE_ERROR")
    job.invokeOnCompletion(onCancelling = true) { cause ->
      if (cause is CancellationException) {
        handleCancellation()
      }
      else {
        // Sanity check: completed or failed means the `run` was executed => the reference must be cleared.
        check(runnableRef.get() == null)
      }
    }
  }

  override fun run() {
    // Clear the reference to avoid scheduling the runnable again when cancelled.
    val runnable = runnableRef.getAndSet(null)
    if (runnable == null) {
      // Cleared in `handleCancellation`.
      // This code path means that the coroutine was cancelled externally after being scheduled.
    }
    else {
      runnable.run()
    }
  }

  private fun handleCancellation() {
    // Clear the reference so this runnable does nothing in `run`.
    val runnable = runnableRef.getAndSet(null)
    if (runnable == null) {
      // Cleared in `run`.
      // This code path means that the cancellation happened concurrently with `run`,
      // or that the coroutine threw CancellationException manually.
    }
    else {
      // Reschedule the original runnable ignoring the modality state
      // to give the cancelled coroutine a chance to clean its resources and complete.
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any())
    }
  }
}
