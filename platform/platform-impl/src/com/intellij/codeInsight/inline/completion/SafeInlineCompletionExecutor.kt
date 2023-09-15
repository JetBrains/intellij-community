// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SafeInlineCompletionExecutor(private val scope: CoroutineScope) {
  private val job = AtomicReference<Job?>(null)
  private val jobsCounter = AtomicInteger(0)
  private val isCancelled = AtomicBoolean(false)

  fun switchJobSafely(block: (suspend CoroutineScope.() -> Unit)?) {
    if (checkNotCancelled()) {
      return
    }

    jobsCounter.incrementAndGet()

    // create a new lazy job
    val nextJob = block?.let { scope.launch(start = CoroutineStart.LAZY, block = block) }
    scope.launch {
      try {
        while (true) {
          val currentJob = job.get()
          currentJob?.cancelAndJoin()
          // if still have this canceled job, let's switch it to a new one
          if (job.compareAndSet(currentJob, nextJob)) {
            LOG.trace("Change inline completion job")
            break
          }
        }
        // start a new actual job if not yet, it may be nextJob OR some even newer job
        job.get()?.let {
          jobsCounter.incrementAndGet()
          it.invokeOnCompletion { jobsCounter.decrementAndGet() }
          it.start()
        }
      } finally {
        jobsCounter.decrementAndGet()
      }
    }
  }

  fun cancel() {
    if (checkNotCancelled()) {
      return
    }
    isCancelled.set(true)
    job.set(null)
    scope.cancel()
  }

  @TestOnly
  suspend fun awaitAll() {
    while (jobsCounter.get() > 0) {
      yield()
    }
  }

  private fun checkNotCancelled(): Boolean {
    val isCancelled = isCancelled.get()
    if (isCancelled) {
      LOG.error("Inline completion executor is cancelled.")
    }
    return isCancelled
  }

  companion object {
    private val LOG = thisLogger()
  }
}
