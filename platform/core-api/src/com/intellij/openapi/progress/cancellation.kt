// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
fun <T> ensureCurrentJob(action: (Job) -> T): T {
  val indicator = ProgressManager.getGlobalProgressIndicator()
  if (indicator != null) {
    return ensureCurrentJob(indicator, action)
  }
  val currentJob = Job(parent = Cancellation.currentJob())
  return executeWithJobAndCompleteIt(currentJob) {
    action(currentJob)
  }
}

private fun <T> ensureCurrentJob(indicator: ProgressIndicator, action: (Job) -> T): T {
  val currentJob = Job(parent = null) // no job parent, the "parent" is the indicator
  return executeWithJobAndCompleteIt(currentJob) {
    val indicatorWatcher = cancelWithIndicator(currentJob, indicator)
    try {
      action(currentJob)
    }
    finally {
      indicatorWatcher.cancel()
    }
  }
}

private fun cancelWithIndicator(job: CompletableJob, indicator: ProgressIndicator): Job {
  return CoroutineScope(Dispatchers.IO).launch(CoroutineName("indicator watcher")) {
    while (!indicator.isCanceled) {
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
    try {
      indicator.checkCanceled()
      error("A cancelled indicator must throw PCE")
    }
    catch (pce: ProcessCanceledException) {
      job.completeExceptionally(pce)
    }
  }
}

/**
 * Associates the calling thread with a [job], invokes [action], and completes the job.
 * @return action result
 */
@Internal
fun <X> executeWithJobAndCompleteIt(
  job: CompletableJob,
  action: () -> X,
): X {
  try {
    val result: X = withJob(job, action)
    job.complete()
    return result
  }
  catch (e: Throwable) {
    job.completeExceptionally(e)
    throw e
  }
}
