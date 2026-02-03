// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.progress

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
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
 * launch(Dispatchers.Default + suspender.asContextElement()) {
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
fun coroutineSuspender(active: Boolean = true): CoroutineSuspender = CoroutineSuspenderImpl(active)

fun CoroutineSuspender.asContextElement(): CoroutineContext.Element = CoroutineSuspenderElement(this)

/**
 * Implementation of this interface is thread-safe.
 */
sealed interface CoroutineSuspender {

  fun isPaused(): Boolean

  fun pause()

  fun resume()
}

private val EMPTY_PAUSED_STATE: CoroutineSuspenderState = CoroutineSuspenderState.Paused(emptyArray())

@ApiStatus.Internal
sealed class CoroutineSuspenderState {
  object Active : CoroutineSuspenderState()
  class Paused(val continuations: Array<Continuation<Unit>>) : CoroutineSuspenderState()
}

/**
 * Internal so it's not possible to access this element from the client code,
 * because clients must not be able to pause themselves.
 * The code which calls [coroutineSuspender] must store the reference somewhere,
 * because this code is responsible for pausing/resuming.
 */
@ApiStatus.Internal
object CoroutineSuspenderElementKey : CoroutineContext.Key<CoroutineSuspenderElement>

@ApiStatus.Internal
class CoroutineSuspenderElement(val coroutineSuspender: CoroutineSuspender) : AbstractCoroutineContextElement(CoroutineSuspenderElementKey)

@ApiStatus.Internal
class CoroutineSuspenderImpl(active: Boolean) : CoroutineSuspender {

  private val myState = MutableStateFlow(if (active) CoroutineSuspenderState.Active else EMPTY_PAUSED_STATE)

  @TestOnly
  val state: Flow<CoroutineSuspenderState> = myState

  val isPaused: Flow<Boolean> = myState.map { it is CoroutineSuspenderState.Paused }

  override fun isPaused(): Boolean {
    return myState.value is CoroutineSuspenderState.Paused
  }

  override fun pause() {
    myState.compareAndSet(CoroutineSuspenderState.Active, EMPTY_PAUSED_STATE)
  }

  override fun resume() {
    val oldState = myState.getAndUpdate { CoroutineSuspenderState.Active }
    if (oldState is CoroutineSuspenderState.Paused) {
      for (suspendedContinuation in oldState.continuations) {
        suspendedContinuation.resume(Unit)
      }
    }
  }

  suspend fun checkPaused() {
    while (true) {
      when (val state = myState.value) {
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
