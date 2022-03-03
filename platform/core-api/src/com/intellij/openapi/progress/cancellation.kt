// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.openapi.progress

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.coroutineContext

fun <X> withJob(job: Job, action: () -> X): X = Cancellation.withJob(job, ThrowableComputable(action))

suspend fun <X> withJob(action: (currentJob: Job) -> X): X {
  val currentJob = coroutineContext.job
  return withJob(currentJob) {
    action(currentJob)
  }
}

/**
 * Ensures that the current thread has an [associated job][Cancellation.currentJob].
 *
 * If there is a global indicator, then the new job is created,
 * and it becomes a "child" of the global progress indicator
 * (the cancellation of the indicator is propagated to the job).
 * If there is already an associated job, then it's used as a parent.
 * If there is no job, or indicator, then the new orphan job is created.
 *
 * This method is designed as a bridge to run the code, which is relying on the newer [Cancellation] mechanism,
 * from the code, which is run under older progress indicators.
 *
 * @throws CancellationException if a global indicator or a current job is cancelled
 */
fun <T> executeCancellable(action: (cancellableJob: Job) -> T): T {
  val indicator = ProgressManager.getGlobalProgressIndicator()
  if (indicator != null) {
    return executeCancellable(indicator, action)
  }
  return doExecuteWithChildJob(parent = Cancellation.currentJob(), action)
}

private fun <T> executeCancellable(indicator: ProgressIndicator, action: (Job) -> T): T {
  // no job parent, the "parent" is the indicator
  return doExecuteWithChildJob(parent = null) { childJob ->
    val indicatorWatcher = cancelWithIndicator(childJob, indicator)
    try {
      action(childJob)
    }
    finally {
      indicatorWatcher.cancel()
    }
  }
}

private fun cancelWithIndicator(job: CompletableJob, indicator: ProgressIndicator): Job {
  return CoroutineScope(job).launch(Dispatchers.IO + CoroutineName("indicator watcher")) {
    while (!indicator.isCanceled) {
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
    job.completeExceptionally(ProcessCanceledException())
  }
}

/**
 * Associates the calling thread with a job, invokes [action], and completes the job with its result.
 * @return action result
 */
fun <X> executeWithChildJob(parent: Job, action: (childJob: CompletableJob) -> X): X {
  return doExecuteWithChildJob(parent, action)
}

private fun <X> doExecuteWithChildJob(parent: Job?, action: (childJob: CompletableJob) -> X): X {
  val job = Job(parent)
  return withJob(job) {
    try {
      val result: X = action(job)
      job.complete()
      result
    }
    catch (e: Throwable) {
      job.completeExceptionally(e)
      throw e
    }
  }
}
