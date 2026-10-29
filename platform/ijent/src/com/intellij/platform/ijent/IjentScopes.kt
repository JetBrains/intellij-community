// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * IJent functionality operates with many coroutine scopes. It's easy to confuse them.
 *
 * These tag types help to distinguish different lifetimes and prevent some bugs in compile-time.
 */
@file:JvmName("IjentScopes")

package com.intellij.platform.ijent

import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.toSafeDeferred
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.platform.util.annotations.VisibleToClasses
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.causeChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
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
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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

  @OptIn(InternalCoroutinesApi::class)
  fun createIjentScope(ijentLabel: String): IjentScope {
    // Prevents from logging the error by the default exception handler.
    // Errors are logged explicitly in this function.
    val dummyExceptionHandler = object : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
      override fun handleException(context: CoroutineContext, exception: Throwable) {
        // Nothing.
      }

      override fun toString(): String = "IjentDummyExceptionHandler"
    }

    // This supervisor scope exists only to prevent automatic propagation of EelUnavailableException to the parent scope.
    // Instead, there's a logic below that decides if a specific EelUnavailableException should be propagated to the parent scope.
    val sessionBoundaryScope = s.childScope(ijentLabel, IjentThreadPool.coroutineContext + dummyExceptionHandler, supervisor = true)

    val ijentScope = IjentScope(
      parent = this,
      sessionBoundaryScope = sessionBoundaryScope,
      ijentLabel = ijentLabel,
    )

    // The watcher is a sibling of the IJent scope, not its child. So the children of the IJent scope are only the session work.
    val ijentJob = ijentScope.s.coroutineContext.job

    // A raw failure of a coroutine fails the IJent scope only after the `finally` blocks of that coroutine.
    // From that moment, it is handled in the same way as `destroy`.
    //
    // `invokeOnCompletion` is `@InternalCoroutinesApi`, and it is documented as "shouldn't be used by anyone",
    // but it's used in >20 other places in the repository, and "clean" alternatives are worse.
    ijentJob.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { error ->
      val rootCause = error?.causeChain()?.find { it !is CancellationException }
      if (rootCause is Exception) {
        ijentScope.handleSessionError(rootCause)
      }
    }

    // It completes only when the scope ends without an exit reason. This happens only after an `Error`, for example `OutOfMemoryError`.
    val fatalError = CompletableDeferred<Throwable>()
    ijentJob.invokeOnCompletion { error ->
      when (val rootCause = error?.causeChain()?.find { it !is CancellationException }) {
        null -> {
          // A cancelled session boundary scope means that the parent is cancelled. It is a normal shutdown, not a bug.
          if (error != null && !sessionBoundaryScope.coroutineContext.job.isCancelled) {
            IjentLogger.LIFETIME_LOG.error(
              IllegalStateException("Cancelling IjentScope is prohibited, use IjentScope.destroy() instead", error))
          }
          // Callers of a dead IJent must get EelUnavailableException also after a cancel.
          // This value loses to any exit reason that `destroy` set before.
          val message =
            if (error != null) "IJent scope $ijentLabel was cancelled"
            else "IJent scope $ijentLabel completed"
          ijentScope.completeExitReason(EelUnavailableException.IntendedExit(message, error))
        }
        // The cancelling handler above sees only the first cause. A cancel can come first, and a raw failure of a child after it.
        is Exception -> when {
          ijentScope.isResolvingExitReason -> Unit
          sessionBoundaryScope.coroutineContext.job.isCancelled -> {
            @Suppress("HardCodedStringLiteral")  // Do we really need i18n in these errors?
            val message = "IJent scope $ijentLabel was cancelled"
            ijentScope.completeExitReason(EelUnavailableException.IntendedExit(message, error))
          }
          else -> ijentScope.handleSessionError(rootCause)
        }
        else -> fatalError.complete(rootCause)
      }
    }

    sessionBoundaryScope.launch(start = CoroutineStart.UNDISPATCHED) {
      // It is safe to wait here without cancellation, also when the parent of IJent scopes is cancelled.
      // The cancellation reaches the IJent scope and its children directly from the session boundary scope, not through
      // this watcher. The parent cannot complete before the IJent scope completes anyway, so the wait adds no delay.
      // The wait stops when the exit reason is known or when the IJent scope completes. The rest is local work.
      withContext(NonCancellable) {
        val err: Throwable = select {
          ijentScope.exitReasonAwaiter().onAwait { it }
          fatalError.onAwait { it }
        }

        // Unconditional: the categorized logging below mutes cancellations and expected exits, which leaves a
        // teardown mid-bootstrap with no trace of what felled the scope.
        IjentLogger.LIFETIME_LOG.debug { "$ijentLabel session scope completed, cause: $err" }

        // Has to be read before the scope is cancelled below, otherwise every teardown looks application-initiated.
        val closedByApplication = sessionBoundaryScope.coroutineContext.job.isCancelled

        val canonicalErr = ijentScope.exitReasonOrNull ?: err

        // `destroy` publishes the exit reason first, and only then fails the IJent scope with it, maybe in another thread.
        // The cancellation below must not win that race, otherwise the IJent scope completes with a bare CancellationException.
        if (canonicalErr is Exception && ijentJob.isActive) {
          try {
            ijentScope.s.launch(start = CoroutineStart.UNDISPATCHED) {
              throw canonicalErr
            }
          }
          catch (_: Throwable) {
            // The IJent scope has completed meanwhile.
          }
        }

        sessionBoundaryScope.cancel(CancellationException(canonicalErr.localizedMessage, canonicalErr))

        val propagateToParentScope = when (canonicalErr) {
          // An environment failure or a legitimate exit is not a bug. Any other exception is a bug.
          is EelUnavailableException -> when (canonicalErr) {
            is EelUnavailableException.CommunicationFailure -> false
            is EelUnavailableException.Conclusive -> false
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
@Suppress("WHEN_SUBJECT_CAN_BE_NULL_IN_JAVA")
@Internal
@VisibleToClasses(
    "com.intellij.diagnostic.logs.ijent.IjentLogLevelListener",
    "com.intellij.docker.ijent.DockerEelMachine",
    "com.intellij.docker.ijent.DockerIjentDeployingStrategy",
    "com.intellij.platform.frontend.split.baseline.light.LightIjentRedeploymentTest",
    "com.intellij.platform.ide.impl.wsl.WslIjentUtil",
    "com.intellij.platform.ijent.IjentProcessUtilTest",
    "com.intellij.platform.ijent.IjentScopeTest",
    "com.intellij.platform.ijent.IjentSessionExitReasonTest",
    "com.intellij.platform.ijent.functional.IjentRestartTest",
    "com.intellij.platform.ijent.functional.TcpLocalBootstrapTest",
    "com.intellij.platform.ijent.impl.GrpcIjentUtilTest",
    "com.intellij.platform.ijent.impl.base.ExternalCliCacheDecorator",
    "com.intellij.platform.ijent.impl.base.GrpcIjentApiDelegate",
    "com.intellij.platform.ijent.impl.base.GrpcIjentApiPosix",
    "com.intellij.platform.ijent.impl.base.GrpcIjentApiWindows",
    "com.intellij.platform.ijent.impl.base.GrpcIjentChildProcessDelegate",
    "com.intellij.platform.ijent.impl.base.GrpcIjentChildProcessKt",
    "com.intellij.platform.ijent.impl.base.GrpcIjentExecApiDelegate",
    "com.intellij.platform.ijent.impl.base.GrpcIjentExecPosixApi",
    "com.intellij.platform.ijent.impl.base.GrpcIjentExecWindowsApi",
    "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemApiDelegate",
    "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemApiDelegateKt",
    "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemPosixApi",
    "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemWindowsApi",
    "com.intellij.platform.ijent.impl.base.GrpcIjentHealthCheckInterceptor",
    "com.intellij.platform.ijent.impl.base.GrpcIjentHealthCheckInterceptorTest",
    "com.intellij.platform.ijent.impl.base.GrpcIjentLostTcpConnectionInterceptor",
    "com.intellij.platform.ijent.impl.base.GrpcIjentOpenedFile",
    "com.intellij.platform.ijent.impl.base.GrpcIjentProcessManagementApiDelegate",
    "com.intellij.platform.ijent.impl.base.GrpcIjentReconnectingInterceptor",
    "com.intellij.platform.ijent.impl.base.GrpcIjentReconnectingInterceptorSessionEndTest",
    "com.intellij.platform.ijent.impl.base.GrpcIjentSessionProviderKt",
    "com.intellij.platform.ijent.impl.base.GrpcIjentTunnelsApi",
    "com.intellij.platform.ijent.impl.base.GrpcIjentTunnelsApiTest",
    "com.intellij.platform.ijent.impl.base.GrpcIjentUtilKt",
    "com.intellij.platform.ijent.impl.base.GrpcStreamingMonitoredClientCall",
    "com.intellij.platform.ijent.impl.base.GrpcStreamingMonitoredClientCallTest",
    "com.intellij.platform.ijent.impl.base.IjentExternalCliScope",
    "com.intellij.platform.ijent.impl.base.IjentNativeArchiveApi",
    "com.intellij.platform.ijent.impl.base.IjentSessionImpl",
    "com.intellij.platform.ijent.impl.base.LegacyIjentSpecialRequestProcessor",
    "com.intellij.platform.ijent.impl.base.MultiTransportIjentSpecialRequestProcessor",
    "com.intellij.platform.ijent.impl.base.TcpTunnelsKt",
    "com.intellij.platform.ijent.impl.base.TcpTunnelsTest",
    "com.intellij.platform.ijent.impl.base.transport.IjentTransportCoordinator",
    "com.intellij.platform.ijent.spi.IjentBinaryCacheTest",
    "com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategyUnitTest",
    "com.intellij.platform.ijent.spi.IjentSessionMediatorUtilsTest",
    "com.intellij.platform.ijent.spi.IjentSessionProcessMediatorTest",
    "com.intellij.platform.ijent.spi.IjentShellBootstrapTest",
    "com.intellij.platform.ijent.spi.TestShellCommandStrategy",
    "com.intellij.platform.ijent.spi.TestShellProcessFacade",
    "com.intellij.platform.ijent.spi.TestShellStrategy",
    "com.intellij.platform.ijent.ssh.AbstractIjentSshManagerTest",
    "com.intellij.platform.ijent.ssh.DaemonSshTransportSupervisorTest",
    "com.intellij.platform.ijent.ssh.daemon.DaemonSshTransportSupervisor",
    "com.intellij.platform.ijent.ssh.daemon.SshDeployingStrategy",
    "com.intellij.platform.ijent.ssh.daemon.SupervisedTunnelFacade",
    "com.intellij.platform.ijent.ssh.impl.AbstractIjentSshManager",
    "com.intellij.platform.ijent.ssh.impl.ijentOverSshJ.SshChannelProcessFacade",
    "com.intellij.platform.ijent.ssh.impl.ijentOverSshJ.SshOverShellDeployKt",
    "com.intellij.rd.eel.backend.IjentDeployRemoteApiImpl",
    "com.intellij.rd.eel.frontend.RdIjentDeployingStrategy",
)
class IjentScope internal constructor(
  val parent: ParentOfIjentScopes,
  private val sessionBoundaryScope: CoroutineScope,
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

  override fun toString(): String = "IjentScope(${state.get()})"

  /**
   * The shutdown state of the session.
   *
   * The transitions are:
   * * [State.Active] to [State.Destroying]: [destroy] or a raw failure of the scope with a symptom.
   *   The scope fails at once, and a resolver outside the scope waits for a root cause.
   * * [State.Destroying] to [State.Destroyed]: a root cause comes, or the resolver sets the symptom after the wait.
   * * [State.Active] to [State.Destroyed]: [destroy] or a raw failure with a root cause, or the completion of a cancelled scope.
   *
   * [State.Destroyed] is final. The first exit reason wins.
   */
  private sealed interface State {
    /** The session has no exit reason yet. [exitReasonAwaiter] completes with the exit reason. */
    sealed interface Pending : State {
      val exitReasonAwaiter: CompletableDeferred<Exception>
    }

    /** Nobody started a shutdown of the session. */
    class Active(override val exitReasonAwaiter: CompletableDeferred<Exception>) : Pending {
      override fun toString(): String = "active"
    }

    /** The scope fails because of a symptom, and the resolver waits for a root cause. */
    class Destroying(override val exitReasonAwaiter: CompletableDeferred<Exception>) : Pending {
      override fun toString(): String = "destroying"
    }

    /**
     * The single, canonical reason why the IJent session is not available anymore.
     *
     * The watcher reads it as it is. A caller gets a copy of it from [resolveExitReason].
     *
     * See `platform/ijent/docs/internal/scope-lifetime.md`.
     */
    class Destroyed(val exitReason: Exception) : State {
      override fun toString(): String = "destroyed: $exitReason"
    }
  }

  private val state: AtomicReference<State> = AtomicReference(State.Active(CompletableDeferred()))

  /** The exit reason of the session, or `null` if the session has no exit reason yet. */
  internal val exitReasonOrNull: Exception?
    get() = when (val current = state.get()) {
      is State.Destroyed -> current.exitReason
      is State.Active, is State.Destroying -> null
    }

  /**
   * Returns a deferred that completes with the exit reason.
   *
   * The state becomes [State.Destroyed] a moment before the deferred of [State.Pending] completes.
   * So read [exitReasonOrNull] after the deferred completes, not before.
   */
  internal fun exitReasonAwaiter(): Deferred<Exception> =
    when (val current = state.get()) {
      is State.Pending -> current.exitReasonAwaiter
      is State.Destroyed -> CompletableDeferred(current.exitReason)
    }

  /** `true` while the resolver waits for a root cause. */
  internal val isResolvingExitReason: Boolean
    get() = when (state.get()) {
      is State.Destroying -> true
      is State.Active, is State.Destroyed -> false
    }

  /**
   * Waits while the resolver waits for a root cause, at most for [DEAD_SESSION_RESOLVE_TIMEOUT].
   *
   * A teardown that ends the IJent process calls it first. Otherwise, the exit of the ended process can become the root cause.
   */
  internal suspend fun awaitExitReasonResolution() {
    val exitReasonAwaiter = when (val current = state.get()) {
      is State.Destroying -> current.exitReasonAwaiter
      is State.Active, is State.Destroyed -> return
    }
    withTimeoutOrNull(DEAD_SESSION_RESOLVE_TIMEOUT) {
      exitReasonAwaiter.join()
    }
  }

  /**
   * Sets [reason] as the exit reason, if the session has no exit reason yet.
   *
   * Returns the already set exit reason if [reason] does not become the exit reason.
   */
  internal fun completeExitReason(reason: Exception): Exception? {
    while (true) {
      when (val current = state.get()) {
        is State.Pending -> {
          if (state.compareAndSet(current, State.Destroyed(reason))) {
            current.exitReasonAwaiter.complete(reason)
            return null
          }
        }
        is State.Destroyed -> return current.exitReason
      }
    }
  }

  /**
   * Awaits the canonical exit reason for at most [timeout].
   *
   * Returns `null` if the reason has not been resolved within the bound, so boundary code can fall back to its
   * default behavior without blocking indefinitely.
   *
   * Returns `null` immediately if the IJent scope is not shutting down.
   * The scope shuts down when it is not active, or when [destroy] was called.
   * An API call of an alive session can fail for its own reasons, and the session has no exit reason then.
   * So the function is safe to use as `SafeDeferred.deadSessionMapper`.
   *
   * It is NEVER a cancellation exception.
   *
   * Each call returns a new exception, so the stack trace shows the caller. The exit reason is its cause, and the message is the same.
   * The copy keeps the subtype, see [EelUnavailableException.copyForCaller].
   * An exit reason that is not [EelUnavailableException] is a bug in the session, not in the caller.
   * The watcher reports such a bug one time, and the caller gets [EelUnavailableException.CommunicationFailure] for it.
   *
   * The wait stops when the calling coroutine is cancelled. Use [resolveExitReasonNonCancellable] in a cancelled coroutine.
   */
  suspend fun resolveExitReason(
    timeout: Duration = DEAD_SESSION_RESOLVE_TIMEOUT,
  ): Exception? =
    resolveExitReason(currentCoroutineContext().job, timeout)

  /**
   * The same as the other [resolveExitReason]. The wait skips the children of the scope that contain [callerJob], because they cannot call [destroy].
   */
  internal suspend fun resolveExitReason(callerJob: Job, timeout: Duration): Exception? {
    val exitReason = when (val current = state.get()) {
      is State.Destroyed -> current.exitReason
      is State.Active if (s.coroutineContext.job.isActive) -> null
      is State.Pending -> awaitExitReason(timeout, callerJob)
    }
    return when (exitReason) {
      null -> null
      is EelUnavailableException -> exitReason.copyForCaller()
      else -> EelUnavailableException.CommunicationFailure("The IJent session ended because of a bug", exitReason)
    }
  }

  /** The wait of [resolveExitReason] in a scope that shuts down. */
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun awaitExitReason(timeout: Duration, currentJob: Job): Exception? {
    val current = state.get()
    val exitReasonAwaiter = when (current) {
      is State.Destroyed -> return current.exitReason
      is State.Pending -> current.exitReasonAwaiter
    }

    run {
      val ijentScopeJob = s.coroutineContext.job
      if (!ijentScopeJob.containsJob(currentJob)) {
        // In `Destroying`, the resolver sets the exit reason in time. In `Active`, the completion of the scope sets it,
        // except after an `Error`. The resolver also waits for the completion, so a caller in `Destroying` must not stop on it.
        withTimeoutOrNull(timeout) {
          select {
            exitReasonAwaiter.onJoin { }
            when (current) {
              is State.Active -> ijentScopeJob.onJoin { }
              is State.Destroying -> Unit
            }
          }
        }
        return exitReasonOrNull
      }
    }

    // A caller inside the scope cannot wait for the scope, because the scope waits for the caller. It makes impossible usage of `Job.join` and other simple functions.
    val until = System.nanoTime().nanoseconds + timeout
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
        exitReasonAwaiter.onJoin { false }
        for (child in otherChildren) {
          child.onJoin { true }
        }
      }
    )
    return exitReasonOrNull
  }

  /**
   * Wraps the code of a suspendable [com.intellij.platform.eel.EelApi] function that waits for the work of the session.
   *
   * An error of [body] does not destroy the session, and the function throws it at once and unchanged, in every state of the session.
   * It is an API surface error, for example a wrong argument, or an error that an I/O primitive already classified.
   *
   * The only exception is a [CancellationException] that the caller did not cause. It is a bug, see [rogueCancellationToBug].
   *
   * Only the I/O primitive knows that an error is a symptom of a dying session, for example `wrapGrpcErrors`.
   * It calls [destroy] if the symptom must end the session, and then [resolveExitReason].
   * Use [wrapSystemErrors] if every error of the code must destroy the session.
   */
  suspend inline fun <T> wrapApiSurfaceErrors(body: suspend () -> T): T {
    try {
      return body()
    }
    catch (caughtErr: CancellationException) {
      throw rogueCancellationToBug(caughtErr)
    }
  }

  /**
   * Returns the bug to throw for a [CancellationException] that the caller did not cause.
   * A cancellation of the caller is rethrown at once.
   *
   * The session code does not cancel its coroutines. It calls [destroy].
   * So such a cancellation means that the code awaits a raw [Deferred] or [Job] of the session. Use [toSafeDeferred] for it.
   * The cancellation is the cause of the bug, so the report also shows the cause of the cancellation.
   */
  @PublishedApi
  internal suspend fun rogueCancellationToBug(err: CancellationException): Exception {
    currentCoroutineContext().ensureActive()
    return RuntimeException("Rogue cancellation exception", err)
  }

  /**
   * Wraps code where every error is a system error of the session, for example the start of the session.
   *
   * An error of [body] destroys the session, see [destroy].
   * Then the function throws the exit reason, or the error of [body] if no exit reason comes within [errorResolutionTimeout].
   *
   * A coroutine of [s] fails the session anyway, because [s] is not a supervisor scope.
   * This function also covers the code that catches the error, and it gives a better exit reason to the caller.
   */
  suspend inline fun <T> wrapSystemErrors(
    errorResolutionTimeout: Duration = DEAD_SESSION_RESOLVE_TIMEOUT,
    body: suspend () -> T,
  ): T {
    try {
      return body()
    }
    catch (caughtErr: Exception) {
      throw systemErrorToThrow(caughtErr, errorResolutionTimeout)
    }
  }

  /** The non-inline part of [wrapSystemErrors]. It keeps the bytecode of every call site small. */
  @PublishedApi
  internal suspend fun systemErrorToThrow(caughtErr: Exception, errorResolutionTimeout: Duration): Exception {
    if (caughtErr is CancellationException) {
      return rogueCancellationToBug(caughtErr)
    }

    // Error resolution may happen during finalization. For example, in the code like this:
    // try { doSomething() } finally { ijentScope.destroy(rootCause) }
    // Destroying the parent job now in order to start such processes.
    destroy(caughtErr)

    return resolveExitReasonNonCancellable(errorResolutionTimeout)?.let { preferCallerError(it, caughtErr) } ?: caughtErr
  }

  /**
   * Returns [callerErr] if it already wraps the exit reason that [exitReasonCopy] was made from. Otherwise, returns [exitReasonCopy].
   *
   * The caller's own wrapper can carry more details, for example a suppressed cleanup failure.
   * The shared exit reason itself is never returned, so a caller cannot change it.
   */
  private fun preferCallerError(exitReasonCopy: Exception, callerErr: Exception): Exception {
    val exitReason = exitReasonCopy.cause ?: return exitReasonCopy
    return if (callerErr.causeChain().drop(1).any { it === exitReason }) callerErr else exitReasonCopy
  }

  /**
   * Scopes of IJent process may not be canceled. They always fail with some error.
   * In case when the whole machinery of some IJent process should be canceled, the scope must complete with [EelUnavailableException].
   *
   * The reason:
   * * To avoid "Kotlin silent killers" (aka "rogue CancellationException")
   * * To throw [EelUnavailableException] on any call of a destroyed [com.intellij.platform.eel.EelApi].
   *
   * The kind of [err] decides its role:
   * * [EelUnavailableException.Conclusive] is the root cause. It becomes the exit reason at once.
   *   Use [EelUnavailableException.IntendedExit] for a legitimate exit.
   * * [EelUnavailableException.CommunicationFailure] and any other exception are symptoms.
   *   A resolver outside [s] waits up to [DEAD_SESSION_RESOLVE_TIMEOUT] for a root cause, and uses [err]
   *   only if no root cause comes. The wait ends earlier when [s] completes, because then no code of the session can bring a root cause.
   *   The code that brings a root cause after the failure of [s] runs in `NonCancellable`, for example the wait for the exit code of IJent.
   *
   * An exit reason that is not [EelUnavailableException] means a bug, and the watcher reports it.
   * [resolveExitReason] gives a caller a copy of the exit reason, and it wraps a bug into [EelUnavailableException.CommunicationFailure].
   *
   * The error may not be a [CancellationException].
   *
   * Two root causes can race. This function cannot tell which error is the true root cause.
   * The first error stays the exit reason, and a later root cause is added to it as a suppressed exception.
   * So the report of the session still shows both errors.
   */
  fun destroy(err: Exception) {
    require(err !is CancellationException)
    handleSessionError(err)
    if (s.coroutineContext.job.isActive) {
      // The scope must fail with the exit reason, if it is known. Another error would be suppressed into it once more.
      val errorToThrow = exitReasonOrNull ?: err
      s.launch(start = CoroutineStart.UNDISPATCHED) {
        throw errorToThrow
      }
    }
  }

  /**
   * Changes the state for an error that ends the session: from [destroy], or from a raw failure of [s].
   * It does not fail [s].
   */
  internal fun handleSessionError(err: Exception) {
    when (err as? EelUnavailableException) {
      is EelUnavailableException.Conclusive -> {
        val existingReason = completeExitReason(err)
        if (existingReason != null && existingReason !== err && err !in existingReason.suppressed) {
          existingReason.addSuppressed(err)
        }
        return
      }
      is EelUnavailableException.CommunicationFailure, null -> Unit
    }

    while (true) {
      when (val current = state.get()) {
        is State.Active -> {
          if (state.compareAndSet(current, State.Destroying(current.exitReasonAwaiter))) {
            break
          }
        }
        is State.Destroyed, is State.Destroying -> return
      }
    }

    val ownJob = s.coroutineContext.job
    if (ownJob.isCompleted) {
      // No code of the session is left, so nothing can bring a root cause.
      completeExitReason(err)
      return
    }

    // The resolver is outside the scope, because the scope is failing. `ATOMIC` starts it also in a cancelled session boundary scope.
    sessionBoundaryScope.launch(start = CoroutineStart.ATOMIC) {
      withContext(NonCancellable) {
        val exitReasonAwaiter = exitReasonAwaiter()
        withTimeoutOrNull(DEAD_SESSION_RESOLVE_TIMEOUT) {
          select {
            exitReasonAwaiter.onJoin { }
            ownJob.onJoin { }
          }
        }
      }
      completeExitReason(err)
    }
  }

  companion object Key : CoroutineContext.Key<IjentScope> {
    /**
     * The time that the resolver waits for a root cause after a symptom, see [destroy].
     * It is also the default timeout of [resolveExitReason], [resolveExitReasonNonCancellable] and [wrapSystemErrors].
     *
     * 4 seconds are taken at random, feel free to experiment with the value. However, look for usages before changing.
     */
    @Internal
    val DEAD_SESSION_RESOLVE_TIMEOUT: Duration = 4.seconds
  }
}

/**
 * The same as [IjentScope.resolveExitReason], but the wait does not stop when the calling coroutine is cancelled.
 *
 * The IJent scope is cancelled when IJent dies. So the coroutines of the scope are cancelled too,
 * and they must use this function to deliver the exit reason of IJent to their users.
 */
@VisibleToClasses(
  "com.intellij.platform.ijent.impl.base.GrpcIjentChildProcessKt",
  "com.intellij.platform.ijent.impl.base.GrpcIjentTunnelsApi",
  "com.intellij.platform.ijent.impl.base.GrpcIjentUtilKt",
  "com.intellij.platform.ijent.impl.base.TcpTunnelsKt",
  "com.intellij.platform.ijent.ssh.daemon.DaemonSshTransportSupervisor",
)
suspend fun IjentScope.resolveExitReasonNonCancellable(
  timeout: Duration = IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT,
): Exception? {
  // Inside `NonCancellable`, the job of the caller is not visible.
  val callerJob = currentCoroutineContext().job
  return withContext(NonCancellable) {
    resolveExitReason(callerJob, timeout)
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
@VisibleToClasses(
  "com.intellij.platform.ijent.impl.base.GrpcIjentChildPosixProcess",
  "com.intellij.platform.ijent.impl.base.GrpcIjentChildProcessDelegate",
  "com.intellij.platform.ijent.impl.base.GrpcIjentChildProcessKt",
  "com.intellij.platform.ijent.impl.base.GrpcIjentChildWindowsProcess",
  "com.intellij.platform.ijent.impl.base.GrpcIjentExecApiDelegate",
)
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

private fun Job.containsJob(job: Job): Boolean {
  return this === job || children.any { it.containsJob(job) }
}