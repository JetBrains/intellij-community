// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.equalChanges
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * Helper for building [ComputedDiffViewModel]
 *
 * @param D data to build diff producers
 */
class CodeReviewDiffViewModelComputer<D>(
  private val dataLoadingFlow: Flow<Deferred<D>>,
  private val diffProducerFactory: (D, RefComparisonChange) -> CodeReviewDiffRequestProducer?
) {
  private val changesRequests = MutableSharedFlow<ChangesSelection>(1)

  suspend fun showChanges(selection: ChangesSelection) {
    changesRequests.emit(selection)
  }

  val diffVm: Flow<ComputedResult<DiffProducersViewModel?>> = channelFlow {
    var currentVm: DiffProducersViewModel? = null
    dataLoadingFlow.collectLatest { dataRequest ->
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
      }.collect { selection ->
        val toEmit = try {
          currentVm.handleSelection(data, selection).also {
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
      }
    }
  }

  private fun DiffProducersViewModel?.handleSelection(data: D, selection: ChangesSelection): DiffProducersViewModel? {
    if (selection.changes.isEmpty()) {
      return null
    }

    val currentVm = this?.also { vm ->
      vm.updateProducers { state ->
        val currentChanges = state.getChanges()
        if (currentChanges != selection.changes) {
          val producers = createProducers(data, selection)
          DiffProducersViewModel.State(producers, selection.selectedIdx)
        }
        else if (state.selectedIdx != selection.selectedIdx) {
          state.copy(selectedIdx = selection.selectedIdx)
        }
        else {
          state
        }.also {
          scrollIfNecessary(it.getSelected(), selection)
        }
      }
    } ?: run {
      val producers = createProducers(data, selection)
      DiffProducersViewModel(producers, selection.selectedIdx).also {
        scrollIfNecessary(it.producers.value.getSelected(), selection)
      }
    }
    return currentVm
  }

  private fun createProducers(data: D, selection: ChangesSelection): List<CodeReviewDiffRequestProducer> =
    selection.changes.map {
      diffProducerFactory(data, it) ?: throw IllegalArgumentException("Failed to produce diff producer for $data and $it")
    }
}

private fun scrollIfNecessary(producer: ChangeDiffRequestChain.Producer?, selection: ChangesSelection) {
  if (producer !is CodeReviewDiffRequestProducer) return
  val location = (selection as? ChangesSelection.Precise)?.location
  if (location != null) {
    producer.scrollTo(location)
  }
}

private fun DiffProducersViewModel.State.getChanges(): List<RefComparisonChange> =
  producers.asSequence().filterIsInstance<CodeReviewDiffRequestProducer>().map { it.change }.toList()