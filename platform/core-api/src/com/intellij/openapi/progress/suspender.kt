// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.progress

import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * Coroutine pause-ability is cooperative, the coroutine must periodically invoke [checkCanceled] to achieve that.
 *
 * Example:
 * ```
 * val suspender = coroutineSuspender()
 * launch(Dispatchers.Default + suspender) {
 *   for (item in data) {
 *     checkCanceled()
 *     // process data
 *   }
 * }
 *
 * // later e.g. when user clicks the button:
 * suspender.pause() // after this call the coroutine (and its children) will suspend in the checkCanceled()
 * ```
 *
 * @param active `true` means non-paused, while `false` creates a suspender in paused state
 * @return handle which can be used to pause and resume the coroutine
 * @see checkCanceled
 */
fun coroutineSuspender(active: Boolean = true): CoroutineSuspender = CoroutineSuspenderElement(active)

/**
 * Implementation of this interface is thread-safe.
 */
@ApiStatus.NonExtendable
interface CoroutineSuspender : CoroutineContext {

  fun pause()

  fun resume()
}

private val EMPTY_PAUSED_STATE: CoroutineSuspenderState = CoroutineSuspenderState.Paused(emptyArray())

private sealed class CoroutineSuspenderState {
  object Active : CoroutineSuspenderState()
  class Paused(val continuations: Array<Continuation<Unit>>) : CoroutineSuspenderState()
}

/**
 * Internal so it's not possible to access this element from the client code,
 * because clients must not be able to pause themselves.
 * The code which calls [coroutineSuspender] must store the reference somewhere,
 * because this code is responsible for pausing/resuming.
 */
internal object CoroutineSuspenderElementKey : CoroutineContext.Key<CoroutineSuspenderElement>

internal class CoroutineSuspenderElement(active: Boolean)
  : AbstractCoroutineContextElement(CoroutineSuspenderElementKey),
    CoroutineSuspender {

  private val myState: AtomicReference<CoroutineSuspenderState> = AtomicReference(
    if (active) CoroutineSuspenderState.Active else EMPTY_PAUSED_STATE
  )

  override fun pause() {
    myState.compareAndSet(CoroutineSuspenderState.Active, EMPTY_PAUSED_STATE)
  }

  override fun resume() {
    val oldState = myState.getAndSet(CoroutineSuspenderState.Active)
    if (oldState is CoroutineSuspenderState.Paused) {
      for (suspendedContinuation in oldState.continuations) {
        suspendedContinuation.resume(Unit)
      }
    }
  }

  suspend fun checkPaused() {
    while (true) {
      when (val state = myState.get()) {
        is CoroutineSuspenderState.Active -> return // don't suspend
        is CoroutineSuspenderState.Paused -> suspendCancellableCoroutine { continuation: Continuation<Unit> ->
          val newState = CoroutineSuspenderState.Paused(state.continuations + continuation)
          if (!myState.compareAndSet(state, newState)) {
            // don't suspend here; on the next loop iteration either the CAS will succeed, or the suspender will be in Active state
            continuation.resume(Unit)
          }
        }
      }
    }
  }
}
