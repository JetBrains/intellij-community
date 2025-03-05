// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

/**
 * A view model for a diff view which contents will be computed later
 */
@ApiStatus.Obsolete
interface ComputedDiffViewModel {
  /**
   * Loaded state of producers.
   * Could be loaded very quickly, so debouncing might be a good idea
   */
  val diffVm: StateFlow<ComputedResult<DiffProducersViewModel?>>
}

/**
 * A view model representing a list of [ChangeDiffRequestChain.Producer] with selection
 */
@ApiStatus.Obsolete
class DiffProducersViewModel private constructor(initialState: State) {
  internal constructor() : this(State(emptyList(), -1))

  private val _producers = MutableStateFlow(initialState)
  val producers: StateFlow<State> = _producers.asStateFlow()

  private val selectionMulticaster = EventDispatcher.create(SimpleEventListener::class.java)

  internal fun canNavigate(): Boolean = producers.value.producers.size > 1

  internal fun canSelectPrev(): Boolean = producers.value.selectedIdx > 0
  internal fun selectPrev() = updateSelection { max(selectedIdx - 1, 0) }

  internal fun canSelectNext(): Boolean = producers.value.run { selectedIdx < producers.lastIndex }
  internal fun selectNext() = updateSelection { min(selectedIdx + 1, producers.lastIndex) }

  internal fun select(change: ChangeDiffRequestChain.Producer) = updateSelection { producers.indexOf(change) }

  private fun updateSelection(newIndexSupplier: State.() -> Int) = updateProducers {
    it.copy(selectedIdx = it.newIndexSupplier())
  }

  internal fun updateProducers(newStateSupplier: (State) -> State) {
    _producers.update(newStateSupplier)
    selectionMulticaster.multicaster.eventOccurred()
  }

  /**
   * Listener invoked SYNCHRONOUSLY when selection is changed
   */
  @ApiStatus.Internal
  suspend fun handleSelection(listener: (ChangeDiffRequestChain.Producer?) -> Unit): Nothing {
    val simpleListener = SimpleEventListener { listener(producers.value.getSelected()) }
    try {
      selectionMulticaster.addListener(simpleListener)
      listener(producers.value.getSelected())
      awaitCancellation()
    }
    finally {
      selectionMulticaster.removeListener(simpleListener)
    }
  }

  data class State internal constructor(val producers: List<ChangeDiffRequestChain.Producer>, val selectedIdx: Int) {
    init {
      require(selectedIdx < 0 || selectedIdx in producers.indices)
    }
  }
}

fun DiffProducersViewModel.State.getSelected(): ChangeDiffRequestChain.Producer? = producers.getOrNull(selectedIdx)