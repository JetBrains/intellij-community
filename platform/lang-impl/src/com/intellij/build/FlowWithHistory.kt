// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a way to organize the storage of events history, which can be re-played to new flow subscribers in a consistent way.
 * There's no need to synchronize access to the history data in subclasses, this class guarantees that it won't be accessed concurrently,
 * provided that data is modified only using [updateHistoryAndEmit] calls.
 */
@ApiStatus.Internal
abstract class FlowWithHistory<T : Any>(private val scope: CoroutineScope) {
  // sequential execution ensures consistent snapshot construction
  private val sequentialDispatcher = Dispatchers.Default.limitedParallelism(1)
  // buffering is important to ensure proper ordering between events emission and 'onSubscription' execution
  private val ourFlow = MutableSharedFlow<T>(extraBufferCapacity = Int.MAX_VALUE)

  /**
   * A flow instance that will re-play the accumulated history (produced by [getHistory] method) before reporting newly emitted values.
   * The returned flow never completes.
   */
  fun getFlowWithHistory(): Flow<T> {
    return flow {
      ourFlow.onSubscription {
        getHistory().forEach {
          emit(it)
        }
      }.collect(this)
    }.flowOn(sequentialDispatcher)
  }

  /**
   * Generates a list of events to be reported to a new subscriber.
   *
   * NOTE. The returned list might be accessed concurrently, subclasses shouldn't share their state via the returned value if that state
   * isn't immutable.
   */
  protected abstract fun getHistory(): List<T>

  /**
   * [updater] is expected to modify the history data, and return the event which should be sent to the existing flow subscribers.
   */
  protected fun updateHistoryAndEmit(updater: () -> T?) {
    scope.launch(sequentialDispatcher) {
      updater()?.let {
        ourFlow.emit(it)
      }
    }
  }
}