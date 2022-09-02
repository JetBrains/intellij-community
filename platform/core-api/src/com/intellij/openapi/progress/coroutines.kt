// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.progress

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.replaceThreadContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.util.Computable
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
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
 * Example: running a coroutine under indicator.
 * ```
 * ProgressManager.getInstance().runProcess({
 *   runBlockingCancellable {
 *     someSuspendingFunctionWhichDoesntKnowAboutIndicator()
 *   }
 * }, progress);
 * ```
 *
 * Example 2: running a coroutine under current job.
 * ```
 * launch { // given a coroutine
 *   blockingContext { // installs the current job
 *     runBlockingCancellable { // becomes a child of the current job
 *       someSuspendingFunction()
 *     }
 *   }
 * }
 * ```
 *
 * If this function is invoked without a current job or indicator, then it may block just as a regular [runBlocking].
 * To prevent such usage an exception is logged.
 * Normally, it should not occur in client code, as it's expected to happen only in the newer code which
 * was introduced in a brief moment of time between the initial version (v0) and the current version (v1) of this function.
 * To potentially block, the clients now have to migrate to [runBlockingMaybeCancellable]
 * which repeats exact semantics of v0 w.r.t to cancellation.
 *
 * @throws ProcessCanceledException if [current indicator][ProgressManager.getGlobalProgressIndicator] is cancelled
 * @throws CancellationException if [current job][Cancellation.currentJob] is cancelled
 * @see runUnderIndicator
 * @see runBlocking
 */
fun <T> runBlockingCancellable(action: suspend CoroutineScope.() -> T): T {
  val indicator = ProgressManager.getGlobalProgressIndicator()
  if (indicator != null) {
    return runBlockingCancellable(indicator, action)
  }
  return ensureCurrentJob { currentJob ->
    val context = currentThreadContext() +
                  currentJob +
                  CoroutineName("job run blocking")
    replaceThreadContext(EmptyCoroutineContext).use {
      runBlocking(context, action)
    }
  }
}

/**
 * **DO NOT USE**: if there is no current job or indicator, then the calling code cannot cancel this call from outside.
 * This function is needed for compatibility: the same code could be cancellable when run under job/indicator,
 * and non-cancellable when run in raw context.
 *
 * This function repeats semantics of [runBlockingCancellable] but doesn't log an error when there is no current job or indicator.
 * Instead, it silently creates a new orphan job, and installs it as the [current job][Cancellation.currentJob],
 * which makes inner [runBlockingCancellable] a child of the orphan job.
 */
@Internal
fun <T> runBlockingMaybeCancellable(action: suspend CoroutineScope.() -> T): T {
  return ensureCurrentJobAllowingOrphan {
    runBlockingCancellable(action)
  }
}

@Internal
fun <T> runBlockingCancellable(indicator: ProgressIndicator, action: suspend CoroutineScope.() -> T): T {
  return ensureCurrentJob(indicator) { currentJob ->
    val context = currentThreadContext() +
                  currentJob +
                  CoroutineName("indicator run blocking") +
                  ProgressIndicatorSink(indicator).asContextElement()
    replaceThreadContext(EmptyCoroutineContext).use {
      runBlocking(context, action)
    }
  }
}

/**
 * Switches from a suspending context to the blocking context.
 *
 * The function is marked with `suspend` so it's only callable from a coroutine.
 *
 * This function resets [current thread context][com.intellij.concurrency.currentThreadContext]
 * to the [coroutine context][coroutineContext] of the calling coroutine.
 * It's done because the context propagation should be done by the coroutine framework.
 *
 * Current thread context usually includes [current job][Cancellation.currentJob],
 * which makes [ProgressManager.checkCanceled] work inside [action].
 * [ProcessCanceledException] thrown from `ProgressManager.checkCanceled()` inside the [action] is rethrown as [CancellationException],
 * so the calling code could continue working in the coroutine framework terms.
 *
 * This function is expected to be rarely needed because if some code needs [ProgressManager.checkCanceled],
 * then it, most probably, should work inside a [com.intellij.openapi.application.readAction],
 * which already performs the switch to the blocking context.
 *
 * @see com.intellij.concurrency.currentThreadContext
 */
suspend fun <T> blockingContext(action: () -> T): T {
  val currentContext = coroutineContext.minusKey(ContinuationInterceptor.Key)
  return replaceThreadContext(currentContext).use {
    // this will catch com.intellij.openapi.progress.JobCanceledException
    // and throw original java.util.concurrent.CancellationException instead
    withCurrentJob(currentContext.job, action)
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
@Internal
suspend fun <T> runUnderIndicator(action: () -> T): T {
  val ctx = coroutineContext
  return runUnderIndicator(ctx, action)
}

@Internal
fun <T> runUnderIndicator(ctx: CoroutineContext, action: () -> T): T {
  val job = ctx.job
  job.ensureActive()
  val indicator = ctx.createIndicator()
  return runUnderIndicator(job, indicator, action)
}

private fun CoroutineContext.createIndicator(): ProgressIndicator {
  val contextModality = contextModality()
                        ?: ModalityState.NON_MODAL
  val progressSink = progressSink
  return if (progressSink == null) {
    EmptyProgressIndicator(contextModality)
  }
  else {
    ProgressSinkIndicator(progressSink, contextModality)
  }
}

@Internal
fun <T> runUnderIndicator(job: Job, indicator: ProgressIndicator, action: () -> T): T {
  try {
    return ProgressManager.getInstance().runProcess(Computable {
      // Register handler inside runProcess to avoid cancelling the indicator before even starting the progress.
      // If the Job was canceled while runProcess was preparing,
      // then CompletionHandler is invoked right away and cancels the indicator.
      @OptIn(InternalCoroutinesApi::class)
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
    @OptIn(InternalCoroutinesApi::class)
    throw job.getCancellationException()
  }
}

@Deprecated(
  message = "Method was renamed",
  replaceWith = ReplaceWith("runBlockingCancellable(action)"),
  level = DeprecationLevel.ERROR,
)
fun <T> runSuspendingAction(action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(action)
}

@Deprecated(
  message = "Method was renamed",
  replaceWith = ReplaceWith("runBlockingCancellable(indicator, action)"),
  level = DeprecationLevel.ERROR,
)
fun <T> runSuspendingAction(indicator: ProgressIndicator, action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(indicator, action)
}
