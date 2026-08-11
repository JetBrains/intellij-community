// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * IJent functionality operates with many coroutine scopes. It's easy to confuse them.
 *
 * These tag types help to distinguish different lifetimes and prevent some bugs in compile-time.
 */
@file:JvmName("IjentScopes")

package com.intellij.platform.ijent

import com.intellij.platform.eel.channels.EelDelicateApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration


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
class ParentOfIjentScopes(val s: CoroutineScope) {
  init {
    require(s.coroutineContext[Job] != null) {
      "Scope $s has no Job"
    }
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
class IjentScope @EelDelicateApi constructor(
  val parent: ParentOfIjentScopes,
  val s: CoroutineScope,
) {
  init {
    require(s.coroutineContext[IjentContext.Key] != null) {
      "A CoroutineScope for IjentScope must have an IjentContext."
    }
  }

  @Internal
  class IjentContext : AbstractCoroutineContextElement(Key) {
    /**
     * The single, canonical reason why the IJent session is not available anymore.
     *
     * It is filled authoritatively by the component that actually knows the truth — the session mediator — via
     * [completeExitReason] (see [com.intellij.platform.ijent.spi.IjentSessionMediatorUtils.createProcessScope], which
     * also has an `invokeOnCompletion` safety net). Boundary code that catches a low-level failure of a dead session
     * may resolve this reason via [resolveExitReason] and rethrow it, so that callers always observe
     * [IjentUnavailableException] instead of a raw low-level exception.
     */
    val exitReason: CompletableDeferred<IjentUnavailableException> = CompletableDeferred()

    /**
     * Completes [exitReason] with the canonical, fully-enriched [err] (e.g. a [IjentUnavailableException.CommunicationFailure]
     * that already carries its stderr [com.intellij.openapi.diagnostic.Attachment]).
     *
     * Idempotent and first-completer-wins: subsequent calls (including the mediator's `invokeOnCompletion` safety net)
     * never overwrite an already-set value. Returns `true` if this call actually set the reason.
     */
    fun completeExitReason(err: IjentUnavailableException): Boolean =
      exitReason.complete(err)

    /**
     * Awaits the canonical [exitReason] for at most [timeout].
     *
     * Returns `null` if the reason has not been resolved within the bound, so boundary code can fall back to its
     * default behavior without blocking indefinitely.
     */
    suspend fun resolveExitReason(timeout: Duration): IjentUnavailableException? =
      withTimeoutOrNull(timeout) { exitReason.await() }

    companion object Key : CoroutineContext.Key<IjentContext>
  }
}
