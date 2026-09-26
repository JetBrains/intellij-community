// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.util.IncrementallyComputedValue.Companion.appendPart
import com.intellij.collaboration.util.IncrementallyComputedValue.Companion.complete
import com.intellij.collaboration.util.IncrementallyComputedValue.Companion.withException
import com.intellij.collaboration.util.IncrementallyComputedValue.Companion.withLoading
import com.intellij.openapi.progress.checkCanceled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * A helper for implementing a state of an async computed list
 */
@ApiStatus.Experimental
interface AsyncIncrementalListComputer<T> {
  val state: StateFlow<IncrementallyComputedValue<List<T>>>

  /**
   * Request to compute the next portion of the list
   * Gives no guarantee anything will actually be computed
   */
  fun requestMore()

  companion object {
    fun <T> createIn(scope: CoroutineScope, sequence: ComputableSequence<ListPart<T>>): AsyncIncrementalListComputer<T> =
      createIn(scope, sequence.getComputer())

    fun <T> createIn(
      scope: CoroutineScope,
      computer: SequenceComputer<ListPart<T>>,
      loadAfterDone: Boolean = false,
    ): AsyncIncrementalListComputer<T> =
      AsyncIncrementalListComputerImpl(scope, computer, loadAfterDone)
  }
}

/**
 * @param loadAfterDone if this computer will compute more data after the [computer] returns [SequenceComputer.ComputationOutcome.Done]
 */
private class AsyncIncrementalListComputerImpl<T>(
  cs: CoroutineScope,
  private val computer: SequenceComputer<ListPart<T>>,
  private val loadAfterDone: Boolean,
) : AsyncIncrementalListComputer<T> {
  private val _state = MutableStateFlow(IncrementallyComputedValue.initial<List<T>>())
  override val state: StateFlow<IncrementallyComputedValue<List<T>>> = _state.asStateFlow()

  private val needMoreState = MutableStateFlow(false)
  private val loaderJob = cs.launch(start = CoroutineStart.LAZY) {
    while (true) {
      checkCanceled()
      needMoreState.first { it }
      try {
        loadMore()
        if (_state.value.isComplete && !loadAfterDone) {
          break
        }
      }
      finally {
        needMoreState.value = false
      }
    }
  }

  private suspend fun loadMore() {
    _state.update { it.withLoading(true) }
    try {
      when (val outcome = computer.computeNext()) {
        SequenceComputer.ComputationOutcome.Done -> {
          _state.update { it.withLoading(false).complete { emptyList() } }
        }
        is SequenceComputer.ComputationOutcome.Item<ListPart<T>> -> {
          val listPart = outcome.value
          _state.update {
            it.withLoading(false).appendPart(lastPart = listPart.isLast) { currentList ->
              currentList?.plus(listPart.value) ?: listPart.value
            }
          }
        }
      }
    }
    catch (ce: CancellationException) {
      _state.value = IncrementallyComputedValue.initial()
      throw ce
    }
    catch (e: Exception) {
      _state.update { it.withException(e).withLoading(false) }
    }
  }

  override fun requestMore() {
    needMoreState.value = true
    loaderJob.start()
  }
}