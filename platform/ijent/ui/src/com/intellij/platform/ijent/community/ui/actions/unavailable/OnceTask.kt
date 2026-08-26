// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ijent.community.ui.actions.unavailable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

internal abstract class OnceTask<T, C> {
  private val state: MutableStateFlow<State<T, C>> = MutableStateFlow(State.Uninitialized)

  abstract suspend fun <R> executeUnderLockIfNotAlreadyAcquired(f: suspend () -> R): R

  fun computedValue(): T? = (state.value as? State.Computed)?.value

  suspend fun getOrCompute(
    onComputing: (Deferred<C>) -> Unit,
    action: suspend (CompletableDeferred<C>) -> T
  ): T {
    (state.value as? State.Computed)?.let { return it.value }
    while (true) {
      val computed = executeUnderLockIfNotAlreadyAcquired { executeCriticalSection(onComputing, action) }
      if (computed != null) return computed.value
    }
  }

  private suspend fun executeCriticalSection(
    onComputing: (Deferred<C>) -> Unit,
    action: suspend (CompletableDeferred<C>) -> T
  ): State.Computed<T>? {
    val newComputingState = State.Computing(CompletableDeferred<C>())
    val tookLeadership = state.compareAndSet(State.Uninitialized, newComputingState)
    if (!tookLeadership) {
      return when (val currentState = state.value) {
        is State.Computing -> {
          onComputing(currentState.context)
          awaitComputed()
        }
        is State.Computed -> currentState
        is State.Uninitialized -> null
      }
    }
    onComputing(newComputingState.context)
    val result = try {
      action(newComputingState.context)
    }
    catch (e: Throwable) {
      newComputingState.context.completeExceptionally(e)
      state.compareAndSet(newComputingState, State.Uninitialized)
      throw e
    }
    return State.Computed(result).also { state.value = it }
  }
  private suspend fun awaitComputed(): State.Computed<T>? {
    return when (val settled = state.filterIsInstance<State.NotComputing<T>>().first()) {
      is State.Computed -> settled
      is State.Uninitialized -> null
    }
  }
}

private sealed class State<out T, out C> {
  object Uninitialized : NotComputing<Nothing>()
  class Computing<C>(val context: CompletableDeferred<C>) : State<Nothing,  C>()
  sealed class NotComputing<T> : State<T, Nothing>()
  class Computed<T>(val value: T) : NotComputing<T>()
}