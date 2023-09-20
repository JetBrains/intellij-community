// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicLong

internal class SafeInlineCompletionExecutor(private val scope: CoroutineScope) {

  // Timestamps of jobs are required to understand whether we waited for all requests by some moment
  private val lastRequestedJobTimestamp = AtomicLong(0)
  private val lastExecutedJobTimestamp = AtomicLong(0)

  private val nextTask = Channel<JobWithTimestamp>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    scope.launch {
      var currentJob: Job? = null
      while (isActive) {
        val (nextJob, nextTimestamp) = nextTask.receive()
        currentJob?.cancelAndJoin()
        ensureActive()
        currentJob = nextJob
        currentJob.invokeOnCompletion { lastExecutedJobTimestamp.set(nextTimestamp) }
        currentJob.start()
      }
    }
  }

  @RequiresEdt
  fun switchJobSafely(block: suspend CoroutineScope.() -> Unit) {
    if (checkNotCancelled()) {
      return
    }

    // create a new lazy job
    val nextJob = scope.launch(start = CoroutineStart.LAZY, block = block)
    val jobWithTimestamp = JobWithTimestamp(nextJob, lastRequestedJobTimestamp.incrementAndGet())
    val sendResult = nextTask.trySend(jobWithTimestamp)
    if (!sendResult.isSuccess) {
      LOG.error("Cannot schedule a request.")
    }
  }

  fun cancel() {
    if (checkNotCancelled()) {
      return
    }
    nextTask.cancel()
    scope.cancel()
  }

  @TestOnly
  suspend fun awaitAll() {
    val currentTimestamp = lastRequestedJobTimestamp.get()
    while (lastExecutedJobTimestamp.get() < currentTimestamp) {
      yield()
      assert(scope.isActive) { "Do not call awaitAll when finishing executor." }
    }
  }

  private fun checkNotCancelled(): Boolean {
    val isCancelled = !scope.isActive
    if (isCancelled) {
      LOG.error("Inline completion executor is cancelled.")
    }
    return isCancelled
  }

  private data class JobWithTimestamp(val job: Job, val timestamp: Long)

  companion object {
    private val LOG = thisLogger()
  }
}
