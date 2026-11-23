// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.async.MappingScopedItemsContainer
import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.ui.util.selectedItem
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.onFailure
import com.intellij.collaboration.util.onInProgress
import com.intellij.collaboration.util.onSuccess
import com.intellij.openapi.ListSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest

/**
 * A viewmodel for a diff processor which can show multiple diffs and switch between them
 */
interface CodeReviewDiffProcessorViewModel<C : Any> {
  val changes: StateFlow<ComputedResult<State<C>>?>

  fun showChange(change: C, scrollRequest: DiffViewerScrollRequest? = null)
  fun showChange(changeIdx: Int, scrollRequest: DiffViewerScrollRequest? = null)

  interface State<C : Any> {
    val selectedChanges: ListSelection<C>
  }
}

/**
 * A version of the [CodeReviewDiffProcessorViewModel] which does some pre-processing on the changes and creates a separate
 * view model for each change
 *
 * @param C change type
 * @param CVM change view model type
 */
interface PreLoadingCodeReviewAsyncDiffViewModelDelegate<C : Any, CVM : AsyncDiffViewModel> {
  val changes: Flow<ComputedResult<CodeReviewDiffProcessorViewModel.State<CVM>>?>

  fun showChanges(changes: ListSelection<C>, scrollRequest: DiffViewerScrollRequest? = null)
  fun showChange(change: C, scrollRequest: DiffViewerScrollRequest? = null)

  suspend fun handleSelection(listener: (ListSelection<C>) -> Unit): Nothing

  companion object {
    fun <D : Any, C : Any, CVM : AsyncDiffViewModel> create(
      preloadedDataFlow: Flow<ComputedResult<D>?>,
      changesPreProcessor: Flow<(List<C>) -> List<C>> = flowOf { it },
      createViewModel: CoroutineScope.(D, C) -> CVM,
    ): PreLoadingCodeReviewAsyncDiffViewModelDelegate<C, CVM> =
      PreLoadingCodeReviewAsyncDiffViewModelDelegateImpl(preloadedDataFlow, changesPreProcessor, createViewModel)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class PreLoadingCodeReviewAsyncDiffViewModelDelegateImpl<D : Any, C : Any, CVM : AsyncDiffViewModel>(
  preloadedDataFlow: Flow<ComputedResult<D>?>,
  private val changesPreProcessor: Flow<(List<C>) -> List<C>>,
  private val createViewModel: CoroutineScope.(D, C) -> CVM,
) : PreLoadingCodeReviewAsyncDiffViewModelDelegate<C, CVM> {
  private val delegate: CodeReviewAsyncDiffViewModelDelegate<C> = CodeReviewAsyncDiffViewModelDelegate.create()

  override val changes: Flow<ComputedResult<CodeReviewDiffProcessorViewModel.State<CVM>>?> =
    preloadedDataFlow.transformLatest { dataLoadingResult ->
      dataLoadingResult?.onInProgress {
        emit(ComputedResult.loading())
      }?.onFailure {
        emit(ComputedResult.failure(it))
      }?.onSuccess { data ->
        handleVmsState(data)
      } ?: emit(null)
    }

  private suspend fun FlowCollector<ComputedResult<CodeReviewDiffProcessorViewModel.State<CVM>>?>.handleVmsState(preloadedData: D) {
    coroutineScope {
      val vmsContainer = MappingScopedItemsContainer.byEquality<C, CVM>(this) {
        createViewModel(preloadedData, it)
      }
      var lastProcessedList: List<C> = emptyList()
      changesPreProcessor.distinctUntilChanged().collectLatest { preProcessor ->
        var lastList: List<C>? = null
        delegate.changesToShow.collectScoped { changesState ->
          try {
            val inputList = changesState.selectedChanges.list
            if (inputList != lastList) {
              lastList = inputList
              lastProcessedList = preProcessor(inputList)
              emit(ComputedResult.loading())
              vmsContainer.update(lastProcessedList)
            }
            val vms = vmsContainer.mappedState.value
            val selectedVmIdx = lastProcessedList.indexOf(changesState.selectedChanges.selectedItem)
            val newState = ViewModelsState(ListSelection.createAt(vms, selectedVmIdx), changesState.scrollRequests)
            emit(ComputedResult.success(newState))
          }
          catch (e: Exception) {
            emit(ComputedResult.failure(e))
          }
        }
      }
    }
  }

  override fun showChanges(changes: ListSelection<C>, scrollRequest: DiffViewerScrollRequest?) = delegate.showChanges(changes, scrollRequest)

  override fun showChange(change: C, scrollRequest: DiffViewerScrollRequest?) = delegate.showChange(change, scrollRequest)

  override suspend fun handleSelection(listener: ChangesSelectionListener<C>): Nothing = delegate.handleSelection(listener)
}
