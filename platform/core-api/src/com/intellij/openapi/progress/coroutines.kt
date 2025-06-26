// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.progress

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.progress.internalCreateRawHandleFromContextStepIfExistsAndFresh
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.concurrency.BlockingJob
import com.intellij.util.concurrency.ThreadScopeCheckpoint
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.intellij.IntellijCoroutines
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
 * or yield if the coroutine has a lower priority while a higher priority task is running.
 *
 * @throws CancellationException if the coroutine is canceled. The exception is also thrown if the coroutine is canceled while suspended.
 * @see ensureActive
 * @see coroutineSuspender
 */
suspend fun checkCanceled() {
  val ctx = coroutineContext
  ctx.ensureActive() // standard check first
  val coroutineSuspender = ctx[CoroutineSuspenderElementKey]?.coroutineSuspender
  (coroutineSuspender as? CoroutineSuspenderImpl)?.checkPaused() // will suspend if paused
}

/**
 * Same as [runBlocking], but installs [coroutineContext] of the calling code into [action].
 * This is the recommended way for invoking suspending code from blocking code in IntelliJ Platform.
 *
 * One of the main differences with [runBlocking] is that this function is _cancellable_:
 * ```kotlin
 * val job = launch {
 *   runBlockingCancellable {
 *     while (true) {
 *       coroutineContext.ensureActive()
 *     }
 *   }
 * }
 * job.cancelAndJoin() // this will resume quickly, because the `Job` inside `launch` is propagated to `runBlockingCancellable`
 * ```
 *
 * ### EDT
 *
 * This method is **forbidden on EDT** because it does not pump the event queue.
 * Switch to a BGT, or use [runWithModalProgressBlocking][com.intellij.platform.ide.progress.runWithModalProgressBlocking].
 *
 * ### Non-cancellable `runBlocking`
 *
 * If this function is invoked in a thread without a current job or indicator, then it may block just as a regular [runBlocking].
 * To prevent such a usage, an exception is logged.
 *
 * What to do with that exception? Options:
 * - Make sure this method is called under an indicator by installing one as a thread indicator via [ProgressManager.runProcess].
 * - Fall back to [runBlockingMaybeCancellable]. **It may freeze because nobody can cancel it from outside**.
 *
 * ### Progress reporting
 *
 * - If there is a fresh [currentProgressStep] in the thread context, this function propagates it as it into [action] context.
 * - If invoked under indicator, no reporting from [action] is visible in the indicator.
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
 * @see runBlocking
 */
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> runBlockingCancellable(action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(allowOrphan = false, compensateParallelism = true, action)
}

/**
 * Do not use this overload unless you absolutely certain that you should.
 * Consider using [runBlockingCancellable] without [compensateParallelism] argument instead.
 */
@Internal
@InternalCoroutinesApi
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> runBlockingCancellable(compensateParallelism: Boolean, action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(allowOrphan = false, compensateParallelism = compensateParallelism, action)
}

private fun <T> runBlockingCancellable(allowOrphan: Boolean, compensateParallelism: Boolean, action: suspend CoroutineScope.() -> T): T {
  assertBackgroundThreadAndNoWriteAction()
  return prepareThreadContext { ctx ->
    if (!allowOrphan && ctx[Job] == null) {
      LOG.error(IllegalStateException("There is no ProgressIndicator or Job in this thread, the current job is not cancellable."))
    }
    val (lockContext, cleanup) = getLockContext(ctx)
    try {
      if (compensateParallelism) {
        @OptIn(InternalCoroutinesApi::class)
        IntellijCoroutines.runBlockingWithParallelismCompensation(ctx + lockContext, action)
      }
      else {
        @Suppress("RAW_RUN_BLOCKING")
        runBlocking(ctx + lockContext, action)
      }
    }
    catch (pce: ProcessCanceledException) {
      throw pce
    }
    catch (ce: CancellationException) {
      throw CeProcessCanceledException(ce)
    }
    finally {
      cleanup.finish()
    }
  }
}

private fun getLockContext(currentThreadContext: CoroutineContext): Pair<CoroutineContext, AccessToken> {
  val parallelize = with(ApplicationManager.getApplication()) {
    installThreadContext(currentThreadContext).use {
      isReadAccessAllowed
    }
  }
  return getLockPermitContext(currentThreadContext, parallelize)
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
  return runBlockingCancellable(allowOrphan = true, compensateParallelism = true, action)
}

/**
 * Do not use this overload unless you absolutely certain that you should.
 * Consider using [runBlockingMaybeCancellable] without the [compensateParallelism] argument instead.
 */
@Internal
@InternalCoroutinesApi
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun <T> runBlockingMaybeCancellable(compensateParallelism: Boolean, action: suspend CoroutineScope.() -> T): T {
  return runBlockingCancellable(allowOrphan = true, compensateParallelism = compensateParallelism, action)
}

@Deprecated(
  "This method is public for compatibility. " +
  "It is not supposed to be used outside of the platform. " +
  "Use `runBlockingCancellable` instead."
)
@Internal
@RequiresBlockingContext
fun <T> indicatorRunBlockingCancellable(indicator: ProgressIndicator, action: suspend CoroutineScope.() -> T): T {
  assertBackgroundThreadAndNoWriteAction()
  return prepareIndicatorThreadContext(indicator) { ctx ->
    val context = ctx +
                  CoroutineName("indicator run blocking")
    val (lockContext, cleanup) = getLockPermitContext()
    try {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking(context + lockContext, action)
    }
    catch (pce: ProcessCanceledException) {
      throw pce
    }
    catch (ce: CancellationException) {
      throw CeProcessCanceledException(ce)
    }
    finally {
      cleanup.finish()
    }
  }
}

/**
 * This function is deprecated since IJPL-445
 *
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
 * [ProcessCanceledException] thrown from `ProgressManager.checkCanceled()` is rethrown back to the suspending context.
 *
 * @see com.intellij.concurrency.currentThreadContext
 */
@Suppress("RedundantSuspendModifier")
@Deprecated(message = "Starting from 2024.2, `blockingContext` is installed implicitly", replaceWith = ReplaceWith("action()"))
suspend fun <T> blockingContext(action: () -> T): T {
  return action()
}

/**
 * Executes the given [action] and suspends the coroutine until all the children computations,
 * spawned during the execution of [action], are completed.
 *
 * This function ensures proper tracking of children computations that are executed in different environments,
 * such as different threads (like [com.intellij.openapi.application.Application.invokeLater])
 * or after a certain period of time (like [com.intellij.util.Alarm.addRequest]).
 *
 * If any child throws an exception that is not [CancellationException],
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
 * The execution of the snippet above prints `"ABCDE"` or `"ABDCE"`, but **never** `"ABDEC"`.
 *
 * @param action The function to execute in the blocking context.
 * @return The result of [action] after all its children are completed.
 *
 * @throws Exception if any of the children computations throw an exception.
 *
 * @see [coroutineScope]
 */
suspend fun <T> blockingContextScope(action: () -> T): T {
  val (result, job) = withCurrentThreadCoroutineScope {
    coroutineScope {
      val coroutineContext = coroutineContext
      val blockingJob = BlockingJob(coroutineContext.job)
      rememberElements(blockingJob, coroutineContext)
      blockingContextInner(coroutineContext + blockingJob, action)
    }
  }
  job.join()
  return result
}

/**
 * The same as [withCurrentThreadCoroutineScope], but for non-suspending execution context.
 */
@ApiStatus.Experimental
@RequiresBlockingContext
fun <T> withCurrentThreadCoroutineScopeBlocking(action: () -> T): Pair<T, Job> {
  val currentContext = currentThreadContext()
  val checkpoint = getFixThreadScopeElements(currentContext)
  return installThreadContext(currentContext + checkpoint, true).use {
    val actionResult = try {
      action()
    }
    catch (e: Throwable) {
      checkpoint.context.job.cancel()
      throw e
    }
    actionResult to checkpoint.startWaitingForChildren()
  }
}

/**
 * Executes the given [action] and provides access to [currentThreadCoroutineScope] inside.
 *
 * In contrast to [blockingContextScope], it does **not** wait for
 * IntelliJ Platform asynchronous computations (such as [Application.invokeLater]) inside [action].
 *
 * This is needed to support old scoping code such as [ProgressManager.runProcessWithProgressSynchronously], which defines a _scope_ (bound to modality),
 * but does not fully await for asynchronous computations (like [Application.executeOnPooledThread]) to complete.
 *
 * @return A result of the computation, and a [Job] which binds all coroutines spawned with [currentThreadCoroutineScope]
 */
@ApiStatus.Experimental
suspend fun <T> withCurrentThreadCoroutineScope(action: suspend CoroutineScope.() -> T): Pair<T, Job> {
  val checkpoint = getFixThreadScopeElements(coroutineContext)
  return withContext(checkpoint) {
    val actionResult = try {
      action()
    }
    catch (e: Throwable) {
      checkpoint.context.job.cancel()
      throw e
    }
    actionResult to checkpoint.startWaitingForChildren()
  }
}

private fun getFixThreadScopeElements(context: CoroutineContext): ThreadScopeCheckpoint {
  val totalCoroutinesJob = SupervisorJob(context[Job])
  val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    totalCoroutinesJob.cancel("Exception from children coroutines", throwable)
  }
  return ThreadScopeCheckpoint(context + totalCoroutinesJob + exceptionHandler)
}

/**
 * Returns [CoroutineScope] that corresponds to the caller's chosen context.
 *
 * This method should be the default choice for initiating coroutines in blocking code.
 * This coroutine scope binds the spawned coroutines to the first checkpoint defined by the caller code.
 * For example, you can use this function to launch coroutines in `AnAction.actionPerformed`.
 *
 * The [coroutineContext] of this scope is the same as at the moment of invocation of [blockingContextScope] or [withCurrentThreadCoroutineScope]
 *
 * To enable usage of [currentThreadCoroutineScope], consider using either [blockingContextScope] or [withCurrentThreadCoroutineScopeBlocking]
 *
 * Example:
 *
 * ```kotlin
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
 * fun myTestFunction() = runBlocking {
 *   blockingContextScope {
 *     MyPluginExtension().legacyApiImplementation() // the 'launch' is tracked now
 *   }
 * }
 * ```
 *
 * An alternative is a pattern with a service that exposes the injected coroutine scope:
 * ```
 * @Service(Service.Level.APP)
 * class ScopeService(val scope: CoroutineScope)
 *
 * fun blockingFunction() {
 *   service<ScopeService>().scope.launch {
 *     // ...
 *   }
 * }
 * ```
 * the difference between these two approaches is similar to the difference between [coroutineScope] and [GlobalScope]:
 * the coroutines spawned on the service scope are not controlled by the code that spawned them.
 */
@RequiresBlockingContext
fun currentThreadCoroutineScope(): CoroutineScope {
  val threadContext = currentThreadContext()
  val checkpoint = threadContext[ThreadScopeCheckpoint]
  if (checkpoint == null) {
    LOG.error(IllegalStateException(
      """There is no scoping element in this thread. Current context: ${threadContext}. 
        | Please use `blockingContextScope` or `withCurrentThreadCoroutineScope` to ensure that spawned coroutines are tracked""".trimMargin()))
  }
  @Suppress("RAW_SCOPE_CREATION")
  return if (checkpoint == null) {
    CoroutineScope(threadContext.minusKey(BlockingJob))
  }
  else {
    CoroutineScope(checkpoint.context)
  }
}

@Internal
fun CoroutineContext.prepareForInstallation(): CoroutineContext = this.minusKey(ContinuationInterceptor.Key)

@Internal
@Throws(ProcessCanceledException::class)
internal fun <T> blockingContextInner(currentContext: CoroutineContext, action: () -> T): T {
  val context = currentContext.prepareForInstallation()
  return installThreadContext(context).use {
    action()
  }
}

@Deprecated(message = "This function does not provide an instance of `ProgressIndicator` to `action`", level = DeprecationLevel.HIDDEN)
suspend fun <T> coroutineToIndicator(action: () -> T): T {
  val ctx = coroutineContext
  return contextToIndicator(ctx, action)
}

/**
 * Runs blocking (e.g., Java) code under indicator, which is canceled if the current Job is canceled.
 *
 * This function switches from suspending context to indicator context.
 *
 * Example:
 * ```kotlin
 * launch {
 *   coroutineToIndicator {
 *     val indicator = ProgressManager.getGlobalProgressIndicator() // retrieving the new indicator
 *     someJavaFunctionWhichDoesntKnowAboutCoroutines()
 *   }
 * }
 * ```
 *
 * ### Progress reporting
 *
 * If there is a fresh [currentProgressStep] in the coroutine context, this function [switches it to raw][reportRawProgress].
 * If the step is not fresh, then no reporting from this function is visible to the caller.
 * Please consult [currentProgressStep] for more info about fresh steps.
 *
 * @see runBlockingCancellable
 * @see ProgressManager.runProcess
 */
suspend fun <T> coroutineToIndicator(action: (ProgressIndicator) -> T): T {
  val ctx = coroutineContext
  return contextToIndicator(ctx) {
    action(ProgressManager.getGlobalProgressIndicator())
  }
}

/**
 * Runs blocking (e.g., Java) code under indicator, which is canceled if [current][Cancellation.currentJob] Job is canceled.
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
 * If there is a fresh [currentProgressStep] in the coroutine context, this function [switches it to raw][reportRawProgress].
 * If the step is not fresh, then no reporting from this function is visible to the caller.
 * Please consult [currentProgressStep] for more info about fresh steps.
 */
@Internal
@RequiresBlockingContext
@Throws(ProcessCanceledException::class)
fun <T> blockingContextToIndicator(action: () -> T): T {
  val ctx = currentThreadContext()
  return try {
    contextToIndicator(ctx, action)
  }
  catch (pce: ProcessCanceledException) {
    throw pce
  }
  catch (ce: CancellationException) {
    throw CeProcessCanceledException(ce)
  }
}

@Throws(CancellationException::class)
private fun <T> contextToIndicator(ctx: CoroutineContext, action: () -> T): T {
  val job = ctx.job
  job.ensureActive()
  val contextModality = ctx.contextModality() ?: ModalityState.nonModal()
  val handle = ctx.internalCreateRawHandleFromContextStepIfExistsAndFresh()
  return if (handle != null) {
    handle.use {
      val indicator = RawProgressReporterIndicator(handle.reporter, contextModality)
      jobToIndicator(job, indicator, action)
    }
  }
  else {
    val indicator = EmptyProgressIndicator(contextModality)
    jobToIndicator(job, indicator, action)
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
    throw e
  }
}

private fun assertBackgroundThreadAndNoWriteAction() {
  if (!EDT.isCurrentThreadEdt()) {
    return
  }

  val app = ApplicationManager.getApplication()
  if (!app.isDispatchThread || (app.isUnitTestMode && !Registry.`is`("ide.run.blocking.cancellable.assert.in.tests", false))) {
    return // OK
  }

  if (app.isWriteAccessAllowed && !app.isTopmostReadAccessAllowed) {
    LOG.error(IllegalStateException(
      "'runBlockingCancellable' is forbidden in the Write Action because it may start a long-running computation. This can cause UI freezes.\n" +
      "Consider running this 'runBlockingCancellable' under a read action outside your Write Action'"
    ))
    return
  }

  LOG.error(IllegalStateException(
    "This method is forbidden on EDT because it does not pump the event queue. " +
    "Switch to a BGT, or use com.intellij.openapi.progress.TasksKt.runWithModalProgressBlocking. "
  ))
}

@IntellijInternalApi
@Internal
fun getLockPermitContext(forSharing: Boolean = false): Pair<CoroutineContext, AccessToken> {
  return getLockPermitContext(currentThreadContext(), forSharing)
}

@IntellijInternalApi
@Internal
fun getLockPermitContext(baseContext: CoroutineContext, forSharing: Boolean): Pair<CoroutineContext, AccessToken> {
  val application = ApplicationManager.getApplication()
  return if (application != null) {
    val (context, cleanup) = application.getLockStateAsCoroutineContext(baseContext, forSharing)
    val targetContext = if (EDT.isCurrentThreadEdt()) {
      context + SafeForRunBlockingUnderReadAction
    }
    else {
      context
    }
    targetContext to cleanup
  }
  else {
    EmptyCoroutineContext to AccessToken.EMPTY_ACCESS_TOKEN
  }
}

@IntellijInternalApi
@Internal
fun CoroutineContext.isRunBlockingUnderReadAction(): Boolean {
  val application = ApplicationManager.getApplication()
  return application != null && application.isParallelizedReadAction(this) && application.isReadAccessAllowed && this[SafeForRunBlockingUnderReadAction] == null
}

private object RunBlockingUnderReadActionMarker
  : CoroutineContext.Element,
    CoroutineContext.Key<RunBlockingUnderReadActionMarker> {
  override val key: CoroutineContext.Key<*> get() = this
}

// public only because needed in actions API
@Internal
object SafeForRunBlockingUnderReadAction
  : CoroutineContext.Element,
    CoroutineContext.Key<SafeForRunBlockingUnderReadAction> {
  override val key: CoroutineContext.Key<*> get() = this
}

private fun rememberElements(job: BlockingJob, context: CoroutineContext) {
  context.fold(Unit) { _, element ->
    job.rememberElement(element)
  }
}


/**
 * Assigns a title to a write action. Intended to be invoked with write lock
 */
@RequiresWriteLock
@ApiStatus.Experimental
fun withWriteActionTitle(title: @NlsContexts.ModalProgressTitle String, action: () -> Unit) {
  service<LockingProgressSupport>().withWriteActionProgress(title, action)
}