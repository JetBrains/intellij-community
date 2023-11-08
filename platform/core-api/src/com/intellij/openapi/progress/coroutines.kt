// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.progress

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.util.progress.*
import com.intellij.util.concurrency.BlockingJob
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

private val LOG = Logger.getInstance("#com.intellij.openapi.progress")

/**
 * Checks whether the coroutine is active, and throws [CancellationException] if the coroutine is canceled.
 * This function might suspend if the coroutine is paused,
 * or yield if the coroutine has a lower priority while higher priority task is running.
 *
 * @throws CancellationException if the coroutine is canceled; the exception is also thrown if coroutine is canceled while suspended
 * @see ensureActive
 * @see coroutineSuspender
 */
suspend fun checkCancelled() {
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
 * ### IMPORTANT
 *
 * Coroutines use [currentCoroutineContext] to handle cancellation or to pass [ModalityState] around.
 * [ProgressManager.checkCanceled], [ModalityState.defaultModalityState]
 * (and [Application.invokeAndWait][com.intellij.openapi.application.Application.invokeAndWait] by extension),
 * and many other platform methods **DO NOT work in a coroutine**.
 * - Instead of [ProgressManager.checkCanceled] use [ensureActive] in a coroutine.
 * - [ModalityState] is not expected to be used explicitly. Instead of `invokeAndWait` or `invokeLater` use
 *   `withContext(`[Dispatchers.EDT][com.intellij.openapi.application.EDT]`) {}` in a coroutine.
 *   If actually needed (twink twice), use [contextModality] to obtain the context [ModalityState].
 * - To invoke older code, which cannot be modified but relies on [ProgressManager.checkCanceled] or
 *   [Application.invokeAndWait][com.intellij.openapi.application.Application.invokeAndWait],
 *   use [blockingContext] to switch from a coroutine to the blocking context.
 *
 * ### EDT
 *
 * This method is **forbidden on EDT** because it does not pump the event queue.
 * Switch to a BGT, or use [runWithModalProgressBlocking][com.intellij.openapi.progress.runWithModalProgressBlocking].
 *
 * ### Non-cancellable `runBlocking`
 *
 * If this function is invoked in a thread without a current job or indicator, then it may block just as a regular [runBlocking].
 * To prevent such usage an exception is logged.
 *
 * What to do with that exception? Options:
 * - Make sure this method is called under a context job.
 *   If it's run from a coroutine somewhere deeper in the trace, use [blockingContext] in the latest possible frame.
 * - Make sure this method is called under an indicator by installing one as a thread indicator via [ProgressManager.runProcess].
 * - Fall back to [runBlockingMaybeCancellable]. **It may freeze because nobody can cancel it from outside**.
 *
 * ### Progress reporting
 *
 * - If invoked under indicator, installs [RawProgressReporter], which methods delegate to the indicator, into the [action] context.
 * - If the thread context contains [ProgressReporter], installs it into the [action] context as is.
 * - If the thread context contains [RawProgressReporter], installs it into the [action] context as is.
 *
 * ### Examples
 *
 * #### Running a coroutine inside a code which is run under indicator
 * ```
 * ProgressManager.getInstance().runProcess({
 *   ... // deeper in the trace
 *     runBlockingCancellable {
 *       someSuspendingFunction()
 *     }
 * }, progress);
 * ```
 *
 * #### Running a coroutine inside a code which is run under job
 * ```
 * launch { // given a coroutine
 *   readAction { // suspending read action installs job to the thread context
 *     ... // deeper in the trace
 *       runBlockingCancellable { // becomes a child of the thread context job
 *         someSuspendingFunction()
 *       }
 *   }
 * }
 * ```
 *
 * @throws ProcessCanceledException if [current indicator][ProgressManager.getGlobalProgressIndicator] is cancelled
 * or [current job][Cancellation.currentJob] is cancelled
 * @see coroutineToIndicator
 * @see blockingContext
 * @see blockingContextToIndicator
 * @see runBlocking
 */
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> runBlockingCancellable(action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(allowOrphan = false, action)
}

private fun <T> runBlockingCancellable(allowOrphan: Boolean, action: suspend CoroutineScope.() -> T): T {
  assertBackgroundThreadOrWriteAction()
  return prepareThreadContext { ctx ->
    if (!allowOrphan && ctx[Job] == null && !Cancellation.isInNonCancelableSection()) {
      LOG.error(IllegalStateException("There is no ProgressIndicator or Job in this thread, the current job is not cancellable."))
    }
    try {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking(ctx + readActionContext(), action)
    }
    catch (ce: CancellationException) {
      throw CeProcessCanceledException(ce)
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
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> runBlockingMaybeCancellable(action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(allowOrphan = true, action)
}

@Deprecated(
  "This method is public for compatibility. " +
  "It is not supposed to be used outside of the platform. " +
  "Use `runBlockingCancellable` instead."
)
@Internal
@RequiresBlockingContext
fun <T> indicatorRunBlockingCancellable(indicator: ProgressIndicator, action: suspend CoroutineScope.() -> T): T {
  assertBackgroundThreadOrWriteAction()
  return prepareIndicatorThreadContext(indicator) { ctx ->
    val context = ctx +
                  CoroutineName("indicator run blocking")
    try {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking(context + readActionContext(), action)
    }
    catch (ce: CancellationException) {
      throw CeProcessCanceledException(ce)
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
 * @see com.intellij.concurrency.currentThreadContext
 */
suspend fun <T> blockingContext(action: () -> T): T {
  return try {
    coroutineScope {
      blockingContextInner(coroutineContext, action)
    }
  }
  catch (pce: ProcessCanceledException) {
    throw PceCancellationException(pce)
  }
}

/**
 * Executes the given [action] in a blocking context and suspends the coroutine until all the children computations,
 * spawned during the execution of [action], are completed.
 *
 * This function is a combination of [blockingContext] and [coroutineScope], providing both their functionalities.
 * It ensures proper tracking of children computations that are executed in different environments,
 * such as different threads (like [com.intellij.openapi.application.Application.invokeLater])
 * or after a certain period of time (like [com.intellij.util.Alarm.addRequest]).
 *
 * If any child throws an exception that is not [CancellationException] or [ProcessCanceledException],
 * then [blockingContextScope] cancels the whole tree of spawned children
 * and resumes with this exception when every remaining child completes exceptionally.
 *
 * Example:
 * ```
 * withContext(Dispatchers.EDT) {
 *   print("A")
 *   blockingContextScope {
 *     print("B")
 *     ApplicationManager.getApplication().executeOnPooledThread {
 *       print("C")
 *     }
 *     print("D")
 *   }
 *   print("E")
 * }
 * ```
 * The execution of the snippet above prints `"ABCDE"` or `"ABDCE"`, but never `"ABDEC"`.
 *
 * @param action The function to execute in the blocking context.
 * @return The result of [action] after all its children are completed.
 *
 * @throws Exception if any of the children computations throw an exception.
 *
 * @see [blockingContext]
 * @see [coroutineScope]
 */
suspend fun <T> blockingContextScope(action: () -> T): T {
  return try {
    coroutineScope {
      val coroutineContext = coroutineContext
      blockingContextInner(coroutineContext + BlockingJob(coroutineContext.job), action)
    }
  }
  catch (pce: ProcessCanceledException) {
    throw PceCancellationException(pce)
  }
}

/**
 * Returns [CoroutineScope] that corresponds to the caller's context.
 *
 * This method should be the default choice for initiating coroutines in blocking code.
 * Its advantage is ensuring the alignment of coroutines' context with the blocking code's cancellation strategy from the spawning point.
 *
 * Example:
 *
 * ```
 * suspend fun deepPlatformCode() {
 *   for (extension in SomeExtensionPoint.EP_NAME.extensionList) {
 *     // the platform has not yet designed suspending API for `SomeExtensionPoint`
 *     blockingContextScope {
 *       extension.legacyApiImplementation()
 *     }
 *   }
 * }
 *
 * class MyPluginExtension : SomeExtensionPoint {
 *   override fun legacyApiImplementation() {
 *     // We aim to incorporate coroutines here,
 *     // without waiting for the platform to provide the suspending API
 *     currentThreadScope().launch {
 *       // modern coroutine implementation of old API
 *     }
 *   }
 * }
 *
 * fun myTestFunction = runBlocking {
 *   blockingContextScope {
 *     MyPluginExtension().legacyApiImplementation() // the 'launch' is tracked now
 *   }
 * }
 * ```
 *
 * An alternative approach would be to create a service that exposes the injected coroutine scope;
 * the difference between these two approaches is similar to the difference between [coroutineScope] and [GlobalScope]:
 * the coroutines spawned on the service scope are not controlled by the code that spawned them.
 */
@RequiresBlockingContext
fun currentThreadScope() : CoroutineScope {
  val threadContext = prepareCurrentThreadContext()
  if (threadContext[Job] == null) {
    LOG.error(IllegalStateException(
      """There is no `Job` in this thread, spawned coroutines are not cancellable. 
        | If the transition from coroutines to blocking code happens in the same stack frame as the call to this function, the transition should use `blockingContext`.
        | If the transition occurs in the different stack frame, then the transition should use `blockingContextScope` to set up a `Job` on this frame.""".trimMargin()))
  }
  return CoroutineScope(threadContext)
}


@Internal
fun <T> blockingContext(currentContext: CoroutineContext, action: () -> T): T {
  try {
    return blockingContextInner(currentContext, action)
  }
  catch (pce: ProcessCanceledException) {
    throw PceCancellationException(pce)
  }
}

@Throws(ProcessCanceledException::class)
private fun <T> blockingContextInner(currentContext: CoroutineContext, action: () -> T): T {
  val context = currentContext.minusKey(ContinuationInterceptor)
  return installThreadContext(context).use {
    action()
  }
}

/**
 * Runs blocking (e.g. Java) code under indicator, which is canceled if current Job is canceled.
 *
 * This function switches from suspending context to indicator context.
 *
 * Example:
 * ```
 * launch {
 *   coroutineToIndicator {
 *     someJavaFunctionWhichDoesntKnowAboutCoroutines()
 *   }
 * }
 * ```
 *
 * ### Progress reporting
 *
 * - If [ProgressReporter] is found in the coroutine context, throws an error.
 * Please wrap the call into [withRawProgressReporter] to switch context [ProgressReporter] to [RawProgressReporter].
 * - If [RawProgressReporter] is found in the coroutine context, updates of the installed indicator are sent into the reporter.
 *
 * @see runBlockingCancellable
 * @see ProgressManager.runProcess
 */
@Internal
suspend fun <T> coroutineToIndicator(action: () -> T): T {
  val ctx = coroutineContext
  return contextToIndicator(ctx, action)
}

/**
 * Runs blocking (e.g. Java) code under indicator, which is canceled if [current][Cancellation.currentJob] Job is canceled.
 *
 * This function switches from [blockingContext] to indicator context.
 *
 * Example:
 * ```
 * launch {
 *   // suspending, installs current Job
 *   readAction {
 *     blockingContextToIndicator {
 *       // ProgressManager.getGlobalProgressIndicator() available here
 *     }
 *   }
 * }
 * ```
 *
 * ### Progress reporting
 *
 * - If [ProgressReporter] is found in the thread context, throws an error.
 * Please wrap the appropriate call into [withRawProgressReporter] to switch context [ProgressReporter] to [RawProgressReporter].
 * For example:
 * ```
 * launch {
 *   // inside a coroutine
 *   withBackgroundProgress(...) { // installs ProgressReporter into coroutine context
 *     ...
 *     readAction { // installs coroutine context as thread context
 *       ...
 *       // at this point the thread context has ProgressReporter, the following will throw
 *       blockingContextToIndicator {
 *         someOldCodeWhichReliesOntoExistenceOfIndicator()
 *       }
 *       ...
 *     }
 *     ...
 *   }
 * }
 * ```
 * In the example, either `readAction` call or the whole action, which was passed into [withBackgroundProgress][com.intellij.openapi.progress.withBackgroundProgress],
 * should be wrapped into [withRawProgressReporter].
 * - If [RawProgressReporter] is found in the coroutine context, updates of the installed indicator are sent into the reporter.
 */
@Internal
@RequiresBlockingContext
fun <T> blockingContextToIndicator(action: () -> T): T {
  val ctx = currentThreadContext()
  return try {
    contextToIndicator(ctx, action)
  }
  catch (ce: CancellationException) {
    throw CeProcessCanceledException(ce)
  }
}

@Throws(CancellationException::class)
private fun <T> contextToIndicator(ctx: CoroutineContext, action: () -> T): T {
  val job = ctx.job
  job.ensureActive()
  val indicator = ctx.createIndicator()
  return jobToIndicator(job, indicator, action)
}

private fun CoroutineContext.createIndicator(): ProgressIndicator {
  val contextModality = contextModality()
                        ?: ModalityState.nonModal()
  if (progressReporter != null) {
    LOG.error(IllegalStateException(
      "Current context has `ProgressReporter`. " +
      "Please switch to `RawProgressReporter` before switching to indicator. " +
      "See 'Progress reporting' in `coroutineToIndicator` and/or `blockingContextToIndicator`.\n" +
      "Current context: $this"
    ))
    return EmptyProgressIndicator(contextModality)
  }
  val reporter = rawProgressReporter
  return if (reporter == null) {
    EmptyProgressIndicator(contextModality)
  }
  else {
    RawProgressReporterIndicator(reporter, contextModality)
  }
}

@Throws(CancellationException::class)
@Internal
fun <T> jobToIndicator(job: Job, indicator: ProgressIndicator, action: () -> T): T {
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
    if (job.isCancelled) {
      @OptIn(InternalCoroutinesApi::class)
      throw job.getCancellationException()
    }
    throw PceCancellationException(e)
  }
}

private fun assertBackgroundThreadOrWriteAction() {
  if (!EDT.isCurrentThreadEdt()) {
    return
  }

  val app = ApplicationManager.getApplication()
  if (!app.isDispatchThread || app.isWriteAccessAllowed || app.isUnitTestMode) {
    return // OK
  }

  LOG.error(IllegalStateException(
    "This method is forbidden on EDT because it does not pump the event queue. " +
    "Switch to a BGT, or use com.intellij.openapi.progress.TasksKt.runWithModalProgressBlocking. "
  ))
}

@IntellijInternalApi
@Internal
fun readActionContext(): CoroutineContext {
  val application = ApplicationManager.getApplication()
  return if (application != null && application.isReadAccessAllowed) {
    RunBlockingUnderReadActionMarker
  }
  else {
    EmptyCoroutineContext
  }
}

@IntellijInternalApi
@Internal
fun CoroutineContext.isRunBlockingUnderReadAction(): Boolean {
  return this[RunBlockingUnderReadActionMarker] != null
}

private object RunBlockingUnderReadActionMarker
  : CoroutineContext.Element,
    CoroutineContext.Key<RunBlockingUnderReadActionMarker> {
  override val key: CoroutineContext.Key<*> get() = this
}

@ApiStatus.ScheduledForRemoval
@Deprecated(
  message = "Method was renamed. Don't use",
  replaceWith = ReplaceWith("indicatorRunBlockingCancellable(indicator, action)"),
  level = DeprecationLevel.ERROR,
)
@RequiresBlockingContext
fun <T> runBlockingCancellable(indicator: ProgressIndicator, action: suspend CoroutineScope.() -> T): T {
  @Suppress("DEPRECATION")
  return indicatorRunBlockingCancellable(indicator, action)
}

@ApiStatus.ScheduledForRemoval
@Deprecated(
  message = "Method was renamed",
  replaceWith = ReplaceWith("coroutineToIndicator(action)"),
  level = DeprecationLevel.ERROR,
)
suspend fun <T> runUnderIndicator(action: () -> T): T {
  return coroutineToIndicator(action)
}
