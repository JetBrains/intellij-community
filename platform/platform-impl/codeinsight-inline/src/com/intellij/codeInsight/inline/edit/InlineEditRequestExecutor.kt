// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.edit

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@JvmInline
@ApiStatus.Internal
value class InlineEditRequestJob(private val job: Job) : Disposable {
  @RequiresEdt
  override fun dispose() {
    job.cancel()
  }
}

@ApiStatus.Internal
sealed interface InlineEditRequestExecutor : Disposable {

  @RequiresEdt
  fun switchRequest(onJobCreated: (InlineEditRequestJob) -> Unit, newRequest: suspend CoroutineScope.() -> Unit)

  @RequiresEdt
  fun cancelActiveRequest()

  @TestOnly
  @RequiresEdt
  suspend fun awaitActiveRequest()

  companion object {
    fun create(parentScope: CoroutineScope): InlineEditRequestExecutor {
      return InlineEditRequestExecutorImpl(parentScope)
    }
  }
}

private class InlineEditRequestExecutorImpl(parentScope: CoroutineScope) : InlineEditRequestExecutor {

  private val scope = parentScope.childScope(name = "Inline Edit Request Executor", supervisor = true)

  // Timestamps of jobs are required to understand whether we waited for all requests by some moment
  private val lastRequestedTimestamp = AtomicLong(0)
  private val lastExecutedTimestamp = AtomicLong(0)

  private val nextTask = Channel<Request>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    scope.launch {
      val currentJob = AtomicReference<Job>()
      while (isActive) {
        val nextRequest = nextTask.receive()
        val nextTimestamp = nextRequest.timestamp
        currentJob.get()?.cancelAndJoin()
        currentJob.set(null)

        // Workaround because there is some unexpected race with the Coroutine Completion Handler
        // Timestamps are only increasing, so nothing is broken
        setLastExecutedToAtLeast(nextTimestamp - 1)
        checkCanceled()

        val nextJob = when (nextRequest) {
          is Request.Execute -> nextRequest.job
          is Request.Cancel -> null
        }

        when (nextJob) {
          null -> {
            setLastExecutedToAtLeast(nextTimestamp)
          }
          else -> {
            currentJob.set(nextJob)
            nextJob.invokeOnCompletion { _ ->
              currentJob.set(null) // IJPL-159913
              setLastExecutedToAtLeast(nextTimestamp)
            }
            nextJob.start()
          }
        }
      }
    }
  }

  @RequiresEdt
  override fun switchRequest(onJobCreated: (InlineEditRequestJob) -> Unit, newRequest: suspend CoroutineScope.() -> Unit) {
    ThreadingAssertions.assertEventDispatchThread()
    if (checkNotCancelled()) {
      return
    }

    val nextJob = scope.launch(ClientId.coroutineContext(), start = CoroutineStart.LAZY, block = newRequest)
    onJobCreated(InlineEditRequestJob(nextJob))
    val jobWithTimestamp = Request.Execute(nextJob, lastRequestedTimestamp.incrementAndGet())
    val sendResult = nextTask.trySend(jobWithTimestamp)
    if (!sendResult.isSuccess) {
      LOG.error("[Inline Edit] Cannot schedule a request.")
    }
  }

  override fun cancelActiveRequest() {
    ThreadingAssertions.assertEventDispatchThread()
    if (checkNotCancelled()) {
      return
    }
    nextTask.trySend(Request.Cancel(lastRequestedTimestamp.incrementAndGet()))
  }

  @TestOnly
  override suspend fun awaitActiveRequest() {
    ThreadingAssertions.assertEventDispatchThread()
    val currentTimestamp = lastRequestedTimestamp.get()
    while (lastExecutedTimestamp.get() < currentTimestamp) {
      yield()
    }
  }

  override fun dispose() {
    if (!scope.isActive) {
      return
    }
    nextTask.cancel()
    scope.cancel()
  }

  private fun setLastExecutedToAtLeast(atLeastTimestamp: Long) {
    while (true) {
      val currentTimestamp = lastExecutedTimestamp.get()
      if (currentTimestamp >= atLeastTimestamp) {
        return
      }
      if (lastExecutedTimestamp.compareAndSet(currentTimestamp, atLeastTimestamp)) {
        return
      }
    }
  }

  private fun checkNotCancelled(): Boolean {
    val isCancelled = !scope.isActive
    if (isCancelled) {
      LOG.error("[Inline Edit] Executor is already cancelled.")
    }
    return isCancelled
  }

  private sealed interface Request {
    val timestamp: Long

    data class Execute(val job: Job, override val timestamp: Long) : Request
    data class Cancel(override val timestamp: Long) : Request
  }

  companion object {
    private val LOG = thisLogger()
  }
}
