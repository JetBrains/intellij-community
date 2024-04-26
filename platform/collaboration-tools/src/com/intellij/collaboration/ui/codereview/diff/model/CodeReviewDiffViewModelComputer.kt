// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.*
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Helper for building [ComputedDiffViewModel]
 *
 * @param D data to build diff producers
 */
class CodeReviewDiffViewModelComputer<D> @ApiStatus.Experimental constructor(
  private val dataLoadingFlow: Flow<Deferred<D>>,
  private val changesSorter: Flow<RefComparisonChangesSorter>,
  private val diffProducerFactory: (D, RefComparisonChange) -> CodeReviewDiffRequestProducer?
) {
  // external usage
  constructor(dataLoadingFlow: Flow<Deferred<D>>, diffProducerFactory: (D, RefComparisonChange) -> CodeReviewDiffRequestProducer?) :
    this(dataLoadingFlow, flowOf(RefComparisonChangesSorter.None), diffProducerFactory)

  private val changesRequests = MutableSharedFlow<ChangesSelection>(1)

  suspend fun showChanges(selection: ChangesSelection) {
    changesRequests.emit(selection)
  }

  val diffVm: Flow<ComputedResult<DiffProducersViewModel?>> = channelFlow {
    var currentVm: DiffProducersViewModel? = null
    dataLoadingFlow.collectLatest { dataRequest ->
      if (!dataRequest.isCompleted) {
        send(ComputedResult.loading())
      }
      val data = try {
        dataRequest.await()
      }
      catch (ce: CancellationException) {
        send(ComputedResult.success(null))
        return@collectLatest
      }
      catch (e: Exception) {
        send(ComputedResult.failure(e))
        return@collectLatest
      }

      changesRequests.distinctUntilChanged { old, new ->
        old.equalChanges(new) && (new !is ChangesSelection.Precise || new.location == null) // always re-request scroll
      }.combine(changesSorter) { selection, sorter ->
        val changes = selection.changes
        if (changes.isEmpty()) {
          currentVm = null
          send(ComputedResult.success(null))
          return@combine
        }

        // show loading?
        val sortedChanges = sorter.sort(changes)

        val toEmit = try {
          val originalSelection = selection.selectedChange
          val selectedIdx = originalSelection?.let { sortedChanges.indexOf(it) } ?: 0
          val scrollLocation = (selection as? ChangesSelection.Precise)?.location

          currentVm.handleSelection(data, sortedChanges, selectedIdx, scrollLocation).also {
            currentVm = it
          }.let {
            ComputedResult.success(it)
          }
        }
        catch (ce: CancellationException) {
          ComputedResult.success(null)
        }
        catch (e: Exception) {
          ComputedResult.failure(e)
        }
        send(toEmit)
      }.collect()
    }
  }

  private fun DiffProducersViewModel?.handleSelection(data: D,
                                                      changes: List<RefComparisonChange>,
                                                      selectedIdx: Int,
                                                      scrollLocation: DiffLineLocation?): DiffProducersViewModel? {
    if (changes.isEmpty()) {
      return null
    }
    val currentVm = this?.also { vm ->
      vm.updateProducers { state ->
        val currentChanges = state.getChanges()
        if (currentChanges != changes) {
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
    } ?: run {
      val producers = createProducers(data, changes)
      DiffProducersViewModel(producers, selectedIdx).also {
        scrollIfPossible(it.producers.value.getSelected(), scrollLocation)
      }
    }
    return currentVm
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