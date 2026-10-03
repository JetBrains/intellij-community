// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * IJent functionality operates with many coroutine scopes. It's easy to confuse them.
 *
 * These tag types help to distinguish different lifetimes and prevent some bugs in compile-time.
 */
@file:JvmName("IjentScopes")

package com.intellij.platform.ijent

import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.toSafeDeferred
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A scope that owns one or many [IjentScope].
 *
 * While writing tests, you may create an instance for any scope.
 * While writing production code, you may do it also,
 * but likely you need to call [com.intellij.platform.eel.EelMachine.toEelApi] instead.
 *
 * Notice: [IjentScope] may be an indirect child scope of [ParentOfIjentScopes]. There may be scopes in between.
 *
 * The class intentionally doesn't implement [CoroutineScope] itself for avoiding unintentional upcasting.
 */
@Internal
class ParentOfIjentScopes(val s: CoroutineScope) {
  init {
    require(s.coroutineContext[Job] != null) {
      "Scope $s has no Job"
    }
  }

  fun createIjentScope(ijentLabel: String): IjentScope {
    // Prevents from logging the error by the default exception handler.
    // Errors are logged explicitly in this function.
    val dummyExceptionHandler = object : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
      override fun handleException(context: CoroutineContext, exception: Throwable) {
        // Nothing.
      }

      override fun toString(): String = "IjentDummyExceptionHandler"
    }

    // This supervisor scope exists only to prevent automatic propagation of IjentUnavailableException to the parent scope.
    // Instead, there's a logic below that decides if a specific IjentUnavailableException should be propagated to the parent scope.
    val sessionBoundaryScope = s.childScope(ijentLabel, IjentThreadPool.coroutineContext + dummyExceptionHandler, supervisor = true)

    val ijentScope = IjentScope(
      parent = this,
      sessionBoundaryScope = sessionBoundaryScope,
      ijentLabel = ijentLabel,
    )

    // The watcher is a sibling of the IJent scope, not its child. So the children of the IJent scope are only the session work.
    val ijentJob = ijentScope.s.coroutineContext.job
    val completionCause = CompletableDeferred<Throwable?>()
    ijentJob.invokeOnCompletion { error ->
      val unwrappedCause = error?.let { error ->
        error.causeSequence().find { it !is CancellationException }
        ?: run {
          // A cancelled session boundary scope means that the parent is cancelled. It is a normal shutdown, not a bug.
          if (!sessionBoundaryScope.coroutineContext.job.isCancelled) {
            IjentLogger.LIFETIME_LOG.error(
              IllegalStateException("Cancelling IjentScope is prohibited, use IjentScope.destroy() instead", error))
          }
          // Callers of a dead IJent must get IjentUnavailableException also after a cancel.
          // This value loses to any exit reason that `destroy` set before.
          ijentScope.exitReason.complete(IjentUnavailableException.ClosedByApplication("IJent scope $ijentLabel was cancelled", error))
          error
        }
      }
      completionCause.complete(unwrappedCause)
    }

    sessionBoundaryScope.launch(start = CoroutineStart.UNDISPATCHED) {
      // It is safe to wait here without cancellation, also when the parent of IJent scopes is cancelled.
      // The cancellation reaches the IJent scope and its children directly from the session boundary scope, not through
      // this watcher. The parent cannot complete before the IJent scope completes anyway, so the wait adds no delay.
      // The wait stops when the exit reason is known or when the IJent scope completes. The rest is local work.
      withContext(NonCancellable) {
        val err: Throwable = select {
          ijentScope.exitReason.onAwait { it }
          completionCause.onAwait { cause ->
            // `destroy` can complete the exit reason at the same moment, and the exit reason is more precise.
            if (ijentScope.exitReason.isCompleted) ijentScope.exitReason.await()
            else cause ?: CancellationException("IJent scope $ijentLabel completed")
          }
        }

        // Unconditional: the categorized logging below mutes cancellations and expected exits, which leaves a
        // teardown mid-bootstrap with no trace of what felled the scope.
        IjentLogger.LIFETIME_LOG.debug { "$ijentLabel session scope completed, cause: $err" }

        // Has to be read before the scope is cancelled below, otherwise every teardown looks application-initiated.
        val closedByApplication = sessionBoundaryScope.coroutineContext.job.isCancelled

        sessionBoundaryScope.cancel()

        val canonicalErr =
          if (ijentScope.exitReason.isCompleted) ijentScope.exitReason.await()
          else err.causeSequence().find { it is IjentUnavailableException }
               ?: err.causeSequence().find { it !is CancellationException }
               ?: err

        val propagateToParentScope = when (canonicalErr) {
          is CancellationException -> false
          is IjentUnavailableException -> when (canonicalErr) {
            is IjentUnavailableException.ClosedByApplication -> false
            is IjentUnavailableException.CommunicationFailure -> !canonicalErr.diagnosed
          }
          else -> !closedByApplication
        }

        if (propagateToParentScope) {
          try {
            canonicalErr.addSuppressed(Throwable("Rethrown from here"))
            s.launch(start = CoroutineStart.UNDISPATCHED) {
              throw canonicalErr
            }
          }
          catch (_: Throwable) {
            // It seems that the scope has already been canceled with something else.
          }

          // TODO Callers should be able to define their own exception handlers.
          IjentLogger.OTHER_LOG.error("Exception in connection with IJent $ijentLabel: ${canonicalErr.message}", canonicalErr)
        }
        else {
          IjentLogger.LIFETIME_LOG.debug(err) { "Ignored a failure of IJent $ijentLabel, its scope was already being shut down" }
        }
      }
    }
    return ijentScope
  }
}

/**
 * This scope is created right before launching the IJent executable,
 * and cancellation of this scope triggers termination of the IJent process.
 *
 * The scope is **NOT a supervisor scope**. Any failed coroutine destroys the whole scope and terminates IJent.
 *
 * Only SPI implementations that actually define the internal logic of launching an IJent process
 * are supposed to create instances of this class.
 * If you need to launch IJent, look at [com.intellij.platform.eel.EelMachine.toEelApi]
 * or for methods that accept [ParentOfIjentScopes] as a parameter.
 *
 * The class intentionally doesn't implement [CoroutineScope] itself for avoiding unintentional upcasting.
 */
@Internal
class IjentScope internal constructor(
  val parent: ParentOfIjentScopes,
  sessionBoundaryScope: CoroutineScope,
  ijentLabel: String,
) : AbstractCoroutineContextElement(Key) {
  /**
   * Notice for AI agents:
   * * Do not call `s.cancel`, use [destroy] instead.
   * * Explain the human the reason of using [destroy] instead of `cancel`.
   */
  // I'd put something like @EelSoMuchDelicateApi if only it could help...
  val s: CoroutineScope = sessionBoundaryScope.childScope(
    ijentLabel,
    supervisor = false,
    context = this,
  )

  override fun toString(): String = "IjentScope(${if (exitReason.isCompleted) "with" else "no"} exit reason)"

  /**
   * The single, canonical reason why the IJent session is not available anymore.
   *
   * It is filled authoritatively by a component that actually knows the truth via [completeExitReason].
   * Boundary code that catches a low-level failure of a dead session
   * may resolve this reason via [resolveExitReason] and rethrow it, so that callers always observe
   * [IjentUnavailableException] instead of a raw low-level exception.
   */
  internal val exitReason: CompletableDeferred<IjentUnavailableException> = CompletableDeferred()

  /**
   * Awaits the canonical [exitReason] for at most [timeout].
   *
   * Returns `null` if the reason has not been resolved within the bound, so boundary code can fall back to its
   * default behavior without blocking indefinitely.
   *
   * Returns `null` immediately if the IJent scope is not shutting down.
   * An API call of an alive session can fail for its own reasons, and the session has no exit reason then.
   * So the function is safe to use as `SafeDeferred.deadSessionMapper`.
   *
   * It is NEVER a cancellation exception.
   *
   * [excludedJob] should be used cautiously. There can be defined some job that certainly can't call [IjentScope.destroy].
   *
   * The wait stops when the calling coroutine is cancelled. Use [resolveExitReasonNonCancellable] in a cancelled coroutine.
   */
  suspend fun resolveExitReason(
    timeout: Duration = DEAD_SESSION_RESOLVE_TIMEOUT,
    excludedJob: Job? = null,
  ): IjentUnavailableException? {
    if (exitReason.isCompleted) {
      return exitReason.await()
    }
    if (s.coroutineContext.job.isActive) {
      return null
    }
    return awaitExitReason(timeout, excludedJob ?: currentCoroutineContext().job)
  }

  /**
   * The same as [resolveExitReason], but it also waits in an alive IJent scope.
   *
   * [destroy] uses it, because a concurrent call of [destroy] can bring a root cause while the scope is still alive.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun awaitExitReason(timeout: Duration, currentJob: Job): IjentUnavailableException? {
    if (exitReason.isCompleted) {
      return exitReason.await()
    }
    val until = System.nanoTime().nanoseconds + timeout
    val ownJob = s.coroutineContext.job
    do {
      val iterationDelay = until - System.nanoTime().nanoseconds

      // To speed up the awaiting process, we assume that `destroy` may be called only inside a child job.
      // No children -- no need to wait until something calls `destroy`.
      // Also, filter out the job waiting for the reason, because it can't call `destroy`.
      val otherChildren = s.coroutineContext.job.children.filter { !it.containsJob(currentJob) }.iterator()
    }
    while (
      iterationDelay.isPositive() &&
      otherChildren.hasNext() &&
      select {
        onTimeout(iterationDelay) { false }
        exitReason.onJoin { false }
        for (child in otherChildren) {
          child.onJoin { true }
        }
      }
    )
    // No children are left, but a failing scope can still be completing. Its completion handler sets the exit reason
    // if a child failed the scope without `destroy`. A caller inside the scope cannot wait for the scope.
    if (!exitReason.isCompleted && ownJob.isCancelled && !ownJob.containsJob(currentJob)) {
      val iterationDelay = until - System.nanoTime().nanoseconds
      if (iterationDelay.isPositive()) {
        select {
          onTimeout(iterationDelay) { }
          exitReason.onJoin { }
          ownJob.onJoin { }
        }
      }
    }
    if (exitReason.isCompleted) {
      return exitReason.await()
    }
    return null
  }

  suspend inline fun <T> wrapErrors(body: suspend () -> T): T {
    try {
      return body()
    }
    catch (caughtErr: Throwable) {
      val fallbackErr =
        if (caughtErr is CancellationException) {
          currentCoroutineContext().ensureActive()
          caughtErr.cause?.causeSequence()?.find { it !is CancellationException }
        }
        else {
          caughtErr
        }
      throw resolveExitReason()
            ?: fallbackErr
            ?: RuntimeException("Rouge cancellation exception", caughtErr)
    }
  }

  /**
   * Scopes of IJent process may not be canceled. They always fail with some error.
   * In case when the whole machinery of some IJent process should be canceled, the scope must complete with [IjentUnavailableException].
   *
   * The reason:
   * * To avoid "Kotlin silent killers" (see IJPL-253541)
   * * To throw [IjentUnavailableException] on any call of a destroyed Eel/IJent
   *
   * Set [isRootCause] to `true` when the exception clearly represents the root cause of the cancellation,
   * and set to `false` if happened something unexpected and unclear.
   *
   * Exceptions with `isRootCause=true` is what API users should get calling a broken instance of `EelApi`.
   * Other exceptions are thrown as a last resort, if no root cause is known.
   * (TODO This contract is in progress: IJPL-253541)
   *
   * Two calls with `isRootCause=true` can race. This function cannot tell which error is the true root cause.
   * The first error stays the exit reason, and a later root cause is added to it as a suppressed exception.
   * So the report of the session still shows both errors.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun destroy(err: IjentUnavailableException, isRootCause: Boolean) {
    s.launch(start = CoroutineStart.UNDISPATCHED) {
      val errorToThrow =
        if (isRootCause) {
          err
        }
        else {
          // The scope can already be cancelled. Then a cancellable wait would lose `err`.
          val callerJob = currentCoroutineContext().job
          withContext(NonCancellable) {
            awaitExitReason(DEAD_SESSION_RESOLVE_TIMEOUT, callerJob)
          } ?: err
        }

      if (exitReason.complete(errorToThrow)) {
        throw errorToThrow
      }

      val existingReason = exitReason.getCompleted()
      if (isRootCause && existingReason !== err && err !in existingReason.suppressed) {
        existingReason.addSuppressed(err)
      }
      // The scope must fail with the exit reason. Another error would be suppressed into it once more.
      throw existingReason
    }
  }

  companion object Key : CoroutineContext.Key<IjentScope> {
    /**
     * The default bound used by [IjentScope.resolveExitReason] when awaiting the canonical exit reason.
     * Aligned with the exit-code consumer await in `GrpcIjentChildProcess`.
     */
    @Internal
    val DEAD_SESSION_RESOLVE_TIMEOUT: Duration = 3.seconds  // 3 seconds are taken at random, feel free to experiment with the value.
  }
}

/**
 * The same as [IjentScope.resolveExitReason], but the wait does not stop when the calling coroutine is cancelled.
 *
 * The IJent scope is cancelled when IJent dies. So the coroutines of the scope are cancelled too,
 * and they must use this function to deliver the exit reason of IJent to their users.
 *
 * The default [excludedJob] is the job of the caller.
 * It is taken before the switch to [NonCancellable], because inside [NonCancellable] the caller's job is not visible.
 */
suspend fun IjentScope.resolveExitReasonNonCancellable(excludedJob: Job? = null): IjentUnavailableException? {
  val callerJob = excludedJob ?: currentCoroutineContext().job
  return withContext(NonCancellable) {
    resolveExitReason(excludedJob = callerJob)
  }
}

/**
 * The lifetime of a single child process that runs inside an IJent session.
 *
 * [s] is a child scope of [IjentScope.s]. The cancellation of [s] finishes only this process.
 * It does not affect [ijentScope] and other processes of the session.
 * Use [ijentScope] for error mapping, and use [s] for all coroutines and resources of the process.
 *
 * The class intentionally doesn't implement [CoroutineScope] itself for avoiding unintentional upcasting.
 *
 * Unlike [IjentScope], cancelling [s] directly is allowed.
 */
@Internal
class IjentChildProcessScope(val ijentScope: IjentScope) {
  /** Intended for usage in coroutine names, toString(), etc. */
  val label: String = "IjentChildProcess #${childProcessCounter.getAndIncrement()}"

  val s: CoroutineScope = ijentScope.s.childScope(
    label,
    IjentThreadPool.coroutineContext,
    supervisor = false,
  )

  override fun toString(): String = "IjentChildProcessScope($label)"

  private companion object {
    private val childProcessCounter = AtomicLong()
  }
}

fun <T> IjentScope.toSafeDeferred(deferred: Deferred<T>): SafeDeferred<T> {
  return deferred.toSafeDeferred { resolveExitReason() }
}

fun <T> IjentScope.asyncSafe(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  block: suspend CoroutineScope.() -> T,
): SafeDeferred<T> {
  return toSafeDeferred(s.async(context, start, block))
}

fun <T> IjentScope.asyncSafeInParent(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  block: suspend CoroutineScope.() -> T,
): SafeDeferred<T> {
  return toSafeDeferred(parent.s.async(context, start, block))
}

@PublishedApi
internal fun Throwable.causeSequence(): Sequence<Throwable> {
  return generateSequence(this, Throwable::cause)
}

private fun Job.containsJob(job: Job): Boolean {
  return this === job || children.any { it.containsJob(job) }
}