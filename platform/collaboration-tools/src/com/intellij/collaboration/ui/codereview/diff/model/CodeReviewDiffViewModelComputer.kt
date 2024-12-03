// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.async.computationState
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.*
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

/**
 * Helper for building [ComputedDiffViewModel]
 *
 * @param D data to build diff producers
 * @see [com.intellij.collaboration.ui.codereview.diff.model.PreLoadingCodeReviewAsyncDiffViewModelDelegate]
 */
@ApiStatus.Obsolete
class CodeReviewDiffViewModelComputer<D> @ApiStatus.Experimental constructor(
  dataLoadingFlow: Flow<ComputedResult<D>>,
  private val changesSorter: Flow<RefComparisonChangesSorter>,
  private val diffProducerFactory: (D, RefComparisonChange) -> CodeReviewDiffRequestProducer?
) {
  @Deprecated("Use a more simplified constructor instead")
  constructor(dataLoadingFlow: Flow<Deferred<D>>, diffProducerFactory: (D, RefComparisonChange) -> CodeReviewDiffRequestProducer?) :
    this(dataLoadingFlow.computationState(), flowOf(RefComparisonChangesSorter.None), diffProducerFactory)

  private val changesRequests = MutableSharedFlow<ChangesSelection>(1)

  suspend fun showChanges(selection: ChangesSelection) {
    changesRequests.emit(selection)
  }

  @ApiStatus.Internal
  fun tryShowChanges(selection: ChangesSelection) {
    changesRequests.tryEmit(selection)
  }

  val diffVm: Flow<ComputedResult<DiffProducersViewModel?>> =
    dataLoadingFlow.mapNotNull { it.result }.distinctUntilChanged().mapScoped { result ->
      result.fold(
        { ComputedResult.success(createProducersVm(it)) },
        { ComputedResult.failure(it) }
      )
    }.withInitial(ComputedResult.loading())

  private fun CoroutineScope.createProducersVm(data: D): DiffProducersViewModel {
    val vm = DiffProducersViewModel()
    launchNow {
      changesRequests.distinctUntilChanged { old, new ->
        old.equalChanges(new) && (new !is ChangesSelection.Precise || new.location == null) // always re-request scroll
      }.combine(changesSorter) { selection, sorter ->
        val changes = selection.changes
        // show loading?
        val sortedChanges = sorter.sort(changes)

        val originalSelection = selection.selectedChange
        val selectedIdx = originalSelection?.let { sortedChanges.indexOf(it) } ?: 0
        val scrollLocation = (selection as? ChangesSelection.Precise)?.location

        vm.setChanges(data, sortedChanges, selectedIdx, scrollLocation)
      }.collect()
    }
    return vm
  }

  private fun DiffProducersViewModel.setChanges(data: D,
                                                changes: List<RefComparisonChange>,
                                                selectedIdx: Int,
                                                scrollLocation: DiffLineLocation?) {
    updateProducers { state ->
      if (state.getChanges() != changes) {
        val producers = createProducers(data, changes)
        DiffProducersViewModel.State(producers, selectedIdx)
      }
      else if (state.selectedIdx != selectedIdx) {
        state.copy(selectedIdx = selectedIdx)
      }
      else {
        state
      }.also {
        scrollIfPossible(it.getSelected(), scrollLocation)
      }
    }
  }

  private fun createProducers(data: D, changes: List<RefComparisonChange>): List<CodeReviewDiffRequestProducer> =
    changes.map { diffProducerFactory(data, it) ?: throw IllegalArgumentException("Failed to produce diff producer for $data and $it") }
}

private fun scrollIfPossible(producer: ChangeDiffRequestChain.Producer?, location: DiffLineLocation?) {
  if (producer !is CodeReviewDiffRequestProducer) return
  if (location != null) {
    producer.scrollTo(location)
  }
}

private fun DiffProducersViewModel.State.getChanges(): List<RefComparisonChange> =
  producers.asSequence().filterIsInstance<CodeReviewDiffRequestProducer>().map { it.change }.toList()