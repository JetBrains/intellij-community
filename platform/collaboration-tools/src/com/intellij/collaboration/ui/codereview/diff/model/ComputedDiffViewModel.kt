// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.min

/**
 * A view model for a diff view which contents will be computed later
 */
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
class DiffProducersViewModel(producers: List<ChangeDiffRequestChain.Producer>, selection: Int) {
  private val _producers = MutableStateFlow(State(producers, selection))
  val producers: StateFlow<State> = _producers.asStateFlow()

  fun canNavigate(): Boolean = producers.value.producers.size > 1

  fun canSelectPrev(): Boolean = producers.value.selectedIdx > 0
  fun selectPrev() = updateSelection { max(selectedIdx - 1, 0) }

  fun canSelectNext(): Boolean = producers.value.run { selectedIdx < producers.lastIndex }
  fun selectNext() = updateSelection { min(selectedIdx + 1, producers.lastIndex) }

  fun select(change: ChangeDiffRequestChain.Producer) = updateSelection { producers.indexOf(change) }

  private fun updateSelection(newIndexSupplier: State.() -> Int) = updateProducers {
    it.copy(selectedIdx = it.newIndexSupplier())
  }

  fun updateProducers(newStateSupplier: (State) -> State) {
    _producers.update(newStateSupplier)
  }

  data class State(val producers: List<ChangeDiffRequestChain.Producer>, val selectedIdx: Int) {
    init {
      require(selectedIdx < 0 || selectedIdx in producers.indices)
    }
  }
}

fun DiffProducersViewModel.State.getSelected(): ChangeDiffRequestChain.Producer? = producers.getOrNull(selectedIdx)