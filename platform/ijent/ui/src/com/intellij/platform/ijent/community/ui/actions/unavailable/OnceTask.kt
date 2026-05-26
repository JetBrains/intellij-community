// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ijent.community.ui.actions.unavailable

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

internal abstract class OnceTask<T> {
  private val state: MutableStateFlow<State<T>> = MutableStateFlow(State.Uninitialized)

  abstract suspend fun <R> executeUnderLockIfNotAlreadyAcquired(f: suspend () -> R): R

  fun computedValue(): T? = (state.value as? State.Computed)?.value

  suspend fun getOrCompute(action: suspend () -> T): T {
    (state.value as? State.Computed)?.let { return it.value }
    while (true) {
      val computed = executeUnderLockIfNotAlreadyAcquired { executeCriticalSection(action) }
      if (computed != null) return computed.value
    }
  }

  private suspend fun executeCriticalSection(action: suspend () -> T): State.Computed<T>? {
    val tookLeadership = state.compareAndSet(State.Uninitialized, State.Computing)
    if (!tookLeadership) {
      val settled = state.filterIsInstance<State.NotComputing<T>>().first()
      return when (settled) {
        is State.Computed -> settled
        is State.Uninitialized -> null
      }
    }
    val result = try {
      action()
    }
    catch (e: Throwable) {
      state.compareAndSet(State.Computing, State.Uninitialized)
      throw e
    }
    return State.Computed(result).also { state.value = it }
  }
}

private sealed class State<out T> {
  object Computing : State<Nothing>()
  sealed class NotComputing<T> : State<T>()
  object Uninitialized : NotComputing<Nothing>()
  class Computed<T>(val value: T) : NotComputing<T>()
}