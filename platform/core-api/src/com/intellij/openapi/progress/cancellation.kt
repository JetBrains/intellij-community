// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.openapi.progress

import com.intellij.concurrency.withThreadContext
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.EmptyCoroutineContext

private val LOG: Logger = Logger.getInstance("#com.intellij.openapi.progress")

fun <X> withCurrentJob(job: Job, action: () -> X): X = Cancellation.withCurrentJob(job, ThrowableComputable(action))

@Deprecated(
  "Renamed to `withCurrentJob`",
  replaceWith = ReplaceWith(
    "withCurrentJob(job, action)",
    "com.intellij.openapi.progress.withCurrentJob"
  )
)
fun <X> withJob(job: Job, action: () -> X): X = withCurrentJob(job, action)

/**
 * Ensures that the current thread has an [associated job][Cancellation.currentJob].
 *
 * If there is a [global indicator][ProgressManager.getGlobalProgressIndicator], then the new job is created,
 * and it becomes a "child" of the global progress indicator
 * (the cancellation of the indicator is cancels the job).
 * Otherwise, if there is already an associated job, then it's used as is.
 * Otherwise, when the current thread does not have an associated job or indicator, then the [IllegalStateException] is thrown.
 *
 * This method is designed as a bridge to run the code, which is relying on the newer [Cancellation] mechanism,
 * from the code, which is run under older progress indicators.
 * This method is expected to continue working when the progress indicator is replaced with a current job.
 *
 * @throws ProcessCanceledException if there was a global indicator and it was cancelled
 * @throws CancellationException if there was a current job it was cancelled
 */
@Internal
fun <T> ensureCurrentJob(action: (Job) -> T): T {
  return ensureCurrentJobInner(allowOrphan = false, action)
}

internal fun <T> ensureCurrentJobAllowingOrphan(action: (Job) -> T): T {
  return ensureCurrentJobInner(allowOrphan = true, action)
}

private fun <T> ensureCurrentJobInner(allowOrphan: Boolean, action: (Job) -> T): T {
  val indicator = ProgressManager.getGlobalProgressIndicator()
  if (indicator != null) {
    return ensureCurrentJob(indicator, action)
  }
  val currentJob = Cancellation.currentJob()
  if (currentJob != null) {
    return action(currentJob)
  }
  if (!allowOrphan) {
    LOG.error("There is no ProgressIndicator or Job in this thread, the current job is not cancellable.")
  }
  val orphanJob = Job(parent = null)
  return executeWithJobAndCompleteIt(orphanJob) {
    action(orphanJob)
  }
}

/**
 * @throws ProcessCanceledException if [indicator] is cancelled,
 * or a child coroutine is started and failed
 */
internal fun <T> ensureCurrentJob(indicator: ProgressIndicator, action: (currentJob: Job) -> T): T {
  val currentJob = Job(parent = null) // no job parent, the "parent" is the indicator
  val indicatorWatcher = cancelWithIndicator(currentJob, indicator)
  val progressModality = ProgressManager.getInstance().currentProgressModality?.asContextElement()
                         ?: EmptyCoroutineContext
  return try {
    ProgressManager.getInstance().silenceGlobalIndicator {
      executeWithJobAndCompleteIt(currentJob) {
        withThreadContext(progressModality).use {
          action(currentJob)
        }
      }
    }
  }
  catch (ce: IndicatorCancellationException) {
    throw ProcessCanceledException(ce)
  }
  catch (ce: CurrentJobCancellationException) {
    throw ProcessCanceledException(ce)
  }
  finally {
    indicatorWatcher.cancel()
  }
}

private fun cancelWithIndicator(job: Job, indicator: ProgressIndicator): Job {
  return CoroutineScope(Dispatchers.IO).launch(CoroutineName("indicator watcher")) {
    while (!indicator.isCanceled) {
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
    try {
      indicator.checkCanceled()
      error("A cancelled indicator must throw PCE")
    }
    catch (pce: ProcessCanceledException) {
      job.cancel(IndicatorCancellationException(pce))
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
    val result: X = withCurrentJob(job, action)
    job.complete()
    return result
  }
  catch (ce: CancellationException) {
    job.cancel(ce)
    throw ce
  }
  catch (e: Throwable) {
    // `job.completeExceptionally(e)` will fail parent Job,
    // which is not desired when this Job is a read action Job.
    //
    // ReadAction.computeCancellable {
    //   throw X
    // }
    // X will be re-thrown, but the caller is not expected to become cancelled
    // since it might catch X and act accordingly.
    //
    // Ideally, completeExceptionally should be used because it's more correct semantically,
    // but read job must not fail its parent regardless of whether the parent is supervisor:
    // https://github.com/Kotlin/kotlinx.coroutines/issues/3409
    job.cancel(CancellationException(null, e))
    throw e
  }
}
