// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.progress

import com.intellij.openapi.util.Computable
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import kotlin.coroutines.coroutineContext

/**
 * Checks whether the coroutine is active, and throws [CancellationException] if the coroutine is canceled.
 * This function might suspend if the coroutine is paused,
 * or yield if the coroutine has a lower priority while higher priority task is running.
 *
 * @throws CancellationException if the coroutine is canceled; the exception is also thrown if coroutine is canceled while suspended
 * @see ensureActive
 * @see coroutineSuspender
 */
suspend fun checkCanceled() {
  val ctx = coroutineContext
  ctx.ensureActive() // standard check first
  ctx[CoroutineSuspenderElementKey]?.checkPaused() // will suspend if paused
}

/**
 * The method has same semantics as [runBlocking], and additionally [action] gets canceled
 * when [the current progress indicator][ProgressManager.getGlobalProgressIndicator] is cancelled,
 * or [the current job][Cancellation.currentJob] is cancelled.
 *
 * This is a bridge for invoking suspending code from blocking code.
 *
 * Example:
 * ```
 * ProgressManager.getInstance().runProcess({
 *   runBlockingCancellable {
 *     someSuspendingFunctionWhichDoesntKnowAboutIndicator()
 *   }
 * }, progress);
 * ```
 * @see runUnderIndicator
 * @see runBlocking
 */
fun <T> runBlockingCancellable(action: suspend CoroutineScope.() -> T): T {
  val indicator = ProgressManager.getGlobalProgressIndicator()
  if (indicator != null) {
    return runBlockingCancellable(indicator, action)
  }
  val currentJob = Cancellation.currentJob()
  if (currentJob != null) {
    // make runBlocking Job a child of the current one to propagate cancellation
    return runBlocking(context = currentJob, block = action)
  }
  // we are not under indicator => just run the action, since nobody will cancel it anyway
  return runBlocking(block = action)
}

fun <T> runBlockingCancellable(indicator: ProgressIndicator, action: suspend CoroutineScope.() -> T): T {
  // we are under indicator => the Job must be canceled when indicator is canceled
  return runBlocking(ProgressIndicatorSink(indicator).asContextElement() + CoroutineName("indicator run blocking")) {
    val indicatorWatchJob = launch(Dispatchers.IO + CoroutineName("indicator watcher")) {
      while (true) {
        if (indicator.isCanceled) {
          // will throw PCE which will cancel the runBlocking Job and thrown further in the caller of runBlockingCancellable
          indicator.checkCanceled()
        }
        delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
      }
    }
    val result = action()
    indicatorWatchJob.cancel()
    result
  }
}

/**
 * Runs blocking (e.g. Java) code under indicator, which is canceled if current Job is canceled.
 *
 * This is a bridge for invoking blocking code from suspending code.
 *
 * Example:
 * ```
 * launch {
 *   runUnderIndicator {
 *     someJavaFunctionWhichDoesntKnowAboutCoroutines()
 *   }
 * }
 * ```
 * @see runBlockingCancellable
 * @see ProgressManager.runProcess
 */
suspend fun <T> runUnderIndicator(action: () -> T): T {
  val ctx = coroutineContext
  return runUnderIndicator(ctx.job, ctx.progressSink, action)
}

@Internal
@Suppress("EXPERIMENTAL_API_USAGE_ERROR")
fun <T> runUnderIndicator(job: Job, progressSink: ProgressSink?, action: () -> T): T {
  job.ensureActive()
  val indicator = if (progressSink == null) EmptyProgressIndicator() else ProgressSinkIndicator(progressSink)
  try {
    return ProgressManager.getInstance().runProcess(Computable {
      // Register handler inside runProcess to avoid cancelling the indicator before even starting the progress.
      // If the Job was canceled while runProcess was preparing,
      // then CompletionHandler is invoked right away and cancels the indicator.
      val completionHandle = job.invokeOnCompletion(onCancelling = true) {
        if (it is CancellationException) {
          indicator.cancel()
        }
      }
      try {
        indicator.checkCanceled()
        action()
      }
      finally {
        completionHandle.dispose()
      }
    }, indicator)
  }
  catch (e: ProcessCanceledException) {
    if (!indicator.isCanceled) {
      // means the exception was thrown manually
      // => treat it as any other exception
      throw e
    }
    // indicator is canceled
    // => CompletionHandler was actually invoked
    // => current Job is canceled
    check(job.isCancelled)
    throw job.getCancellationException()
  }
}

@ScheduledForRemoval(inVersion = "2022.3")
@Deprecated(message = "Method was renamed", replaceWith = ReplaceWith("runBlockingCancellable(action)"))
fun <T> runSuspendingAction(action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(action)
}

@ScheduledForRemoval(inVersion = "2022.3")
@Deprecated(message = "Method was renamed", replaceWith = ReplaceWith("runBlockingCancellable(indicator, action)"))
fun <T> runSuspendingAction(indicator: ProgressIndicator, action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(indicator, action)
}
