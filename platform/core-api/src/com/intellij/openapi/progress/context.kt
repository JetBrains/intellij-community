// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.openapi.progress

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.resetThreadContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.asContextElement
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.BlockingJob
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Deprecated(
  "This function is deprecated because it replaces the whole context. " +
  "Instead, use blockingContext with full context.",
  ReplaceWith("blockingContext(job, action)")
)
fun <X> withCurrentJob(job: Job, action: () -> X): X = blockingContext(job, action)

/**
 * ```
 * launch {
 *   blockingContext {
 *     val blockingJob = Cancellation.currentJob()
 *     executeOnPooledThread {
 *       val executeOnPooledThreadJob = Cancellation.currentJob() // a child of blockingJob
 *       runBlockingCancellable { // child of executeOnPooledThreadJob
 *         blockingContext {
 *           // currentThreadContext() should not contain BlockingJob here
 *           // => BlockingJob is removed during blocking -> coroutine transition in `runBlockingCancellable`
 *           // Same applies for `runWithModalProgressBlocking`
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
internal fun prepareCurrentThreadContext(): CoroutineContext {
  return currentThreadContext().minusKey(BlockingJob)
}

@Internal
@RequiresBlockingContext
fun isInCancellableContext(): Boolean {
  return (ProgressManager.getGlobalProgressIndicator() != null || Cancellation.currentJob() != null) &&
         !Cancellation.isInNonCancelableSection()
}

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
@RequiresBlockingContext
fun <T> prepareThreadContext(action: (CoroutineContext) -> T): T {
  val indicator = ProgressManager.getGlobalProgressIndicator()
  if (indicator != null) {
    return prepareIndicatorThreadContext(indicator, action)
  }
  val currentContext = prepareCurrentThreadContext()
  return resetThreadContext().use {
    if (Cancellation.isInNonCancelableSection()) {
      action(currentContext.minusKey(Job))
    }
    else {
      action(currentContext)
    }
  }
}

/**
 * @throws ProcessCanceledException if [indicator] is cancelled,
 * or a child coroutine is started and failed
 */
internal fun <T> prepareIndicatorThreadContext(indicator: ProgressIndicator, action: (CoroutineContext) -> T): T {
  val context = prepareCurrentThreadContext().minusKey(Job) +
                indicatorContext(indicator)
  if (Cancellation.isInNonCancelableSection()) {
    return ProgressManager.getInstance().silenceGlobalIndicator {
      resetThreadContext().use {
        action(context)
      }
    }
  }
  val currentJob = Job(parent = null) // no job parent, the "parent" is the indicator
  val indicatorWatcher = cancelWithIndicator(currentJob, indicator)
  return try {
    ProgressManager.getInstance().silenceGlobalIndicator {
      resetThreadContext().use {
        action(context + currentJob)
      }.also {
        currentJob.complete()
      }
    }
  }
  catch (t: Throwable) {
    currentJob.completeExceptionally(t)
    throw t
  }
  finally {
    indicatorWatcher.cancel()
  }
}

/**
 * @return [ModalityState] and [ProgressReporter] from [indicator] as [CoroutineContext]
 */
private fun indicatorContext(indicator: ProgressIndicator): CoroutineContext {
  val progressModality = ProgressManager.getInstance().currentProgressModality?.asContextElement()
                         ?: EmptyCoroutineContext
  val reporter = IndicatorRawProgressReporter(indicator).asContextElement()
  return progressModality + reporter
}

private fun cancelWithIndicator(job: Job, indicator: ProgressIndicator): Job {
  @OptIn(DelicateCoroutinesApi::class)
  return GlobalScope.launch(indicatorWatcherDispatcher + CoroutineName("indicator watcher")) {
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

// use elasticity property of the IO dispatcher
@OptIn(ExperimentalCoroutinesApi::class)
private val indicatorWatcherDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(parallelism = 1)
