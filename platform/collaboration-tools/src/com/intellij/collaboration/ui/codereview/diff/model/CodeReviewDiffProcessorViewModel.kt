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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.CopyOnWriteArrayList

@Internal
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
@Internal
interface PreLoadingCodeReviewAsyncDiffViewModelDelegate<C : Any, CVM : AsyncDiffViewModel> {
  val changes: Flow<ComputedResult<CodeReviewDiffProcessorViewModel.State<CVM>>?>

  fun showChanges(changes: ListSelection<C>, scrollRequest: DiffViewerScrollRequest? = null)
  fun showChange(change: C, scrollRequest: DiffViewerScrollRequest? = null)

  suspend fun handleSelection(listener: (ListSelection<C>) -> Unit): Nothing

  companion object {
    fun <D : Any, C : Any, CVM : AsyncDiffViewModel> create(
      preloadedDataFlow: Flow<ComputedResult<D>?>,
      changesPreProcessor: Flow<(List<C>) -> List<C>>,
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
  private val changesToShow = MutableStateFlow(ChangesState<C>())
  private val selectionListeners = CopyOnWriteArrayList<ChangesSelectionListener<C>>()

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
      var lastList: List<C> = emptyList()
      changesPreProcessor.collectLatest { preProcessor ->
        changesToShow.collectScoped { changesState ->
          if (changesState.selectedChanges.list != lastList) {
            emit(ComputedResult.loading())
            val processedList = preProcessor(changesState.selectedChanges.list)
            vmsContainer.update(processedList)
            lastList = changesState.selectedChanges.list
          }
          val mappingState = vmsContainer.mappingState.value
          val vms = mappingState.values.toList()
          val selectedVmIdx = mappingState.keys.indexOf(changesState.selectedChanges.selectedItem)
          val newState = ViewModelsState(ListSelection.createAt(vms, selectedVmIdx), changesState.scrollRequests)
          emit(ComputedResult.success(newState))
        }
      }
    }
  }

  override fun showChanges(changes: ListSelection<C>, scrollRequest: DiffViewerScrollRequest?) {
    val state = changesToShow.updateAndGet {
      if (it.selectedChanges == changes) it else ChangesState(changes)
    }
    notifySelection(state.selectedChanges)

    if (scrollRequest != null) {
      state.scroll(scrollRequest)
    }
  }

  override fun showChange(change: C, scrollRequest: DiffViewerScrollRequest?) {
    val current = changesToShow.value
    val newIdx = current.selectedChanges.list.indexOf(change)
    if (newIdx < 0) return
    val newChanges = ListSelection.createAt(current.selectedChanges.list, newIdx)
    val newState = current.copy(selectedChanges = newChanges)
    if (!changesToShow.compareAndSet(current, newState)) {
      return
    }
    notifySelection(newState.selectedChanges)

    if (scrollRequest != null) {
      newState.scroll(scrollRequest)
    }
  }

  private fun notifySelection(selection: ListSelection<C>) =
    selectionListeners.forEach {
      try {
        it.invoke(selection)
      }
      catch (e: Exception) {
        // notification failed
      }
    }

  override suspend fun handleSelection(listener: ChangesSelectionListener<C>): Nothing {
    try {
      selectionListeners.add(listener)
      listener(changesToShow.value.selectedChanges)
      awaitCancellation()
    }
    finally {
      selectionListeners.remove(listener)
    }
  }

  private data class ChangesState<C : Any>(val selectedChanges: ListSelection<C>) {
    constructor() : this(ListSelection.empty())

    private val _scrollRequests = Channel<DiffViewerScrollRequest>(1, BufferOverflow.DROP_OLDEST)
    val scrollRequests: Flow<DiffViewerScrollRequest> = _scrollRequests.receiveAsFlow()

    fun scroll(cmd: DiffViewerScrollRequest) {
      _scrollRequests.trySend(cmd)
    }
  }

  private data class ViewModelsState<CVM : Any>(
    override val selectedChanges: ListSelection<CVM>,
    override val scrollRequests: Flow<DiffViewerScrollRequest>,
  ) : CodeReviewDiffProcessorViewModel.State<CVM>, DiffViewerScrollRequestProducer
}

private typealias ChangesSelectionListener<C> = (ListSelection<C>) -> Unit
