// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.async.MappingScopedItemsContainer
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewAsyncDiffViewModelDelegate.ChangesState
import com.intellij.collaboration.ui.util.selectedItem
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.ListSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.updateAndGet
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList

@ApiStatus.Internal
interface CodeReviewAsyncDiffViewModelDelegate<C : Any> {
  val changesToShow: StateFlow<ChangesState<C>>

  fun showChanges(changes: ListSelection<C>, scrollRequest: DiffViewerScrollRequest? = null)
  fun showChange(change: C, scrollRequest: DiffViewerScrollRequest? = null)

  suspend fun handleSelection(listener: (ListSelection<C>) -> Unit): Nothing

  data class ChangesState<C : Any>(val selectedChanges: ListSelection<C>) {
    constructor() : this(ListSelection.empty())

    private val _scrollRequests = Channel<DiffViewerScrollRequest>(1, BufferOverflow.DROP_OLDEST)
    val scrollRequests: Flow<DiffViewerScrollRequest> = _scrollRequests.receiveAsFlow()

    fun scroll(cmd: DiffViewerScrollRequest) {
      _scrollRequests.trySend(cmd)
    }
  }

  companion object {
    fun <C : Any> create(): CodeReviewAsyncDiffViewModelDelegate<C> =
      CodeReviewAsyncDiffViewModelDelegateImpl()
  }
}

private class CodeReviewAsyncDiffViewModelDelegateImpl<C : Any>() : CodeReviewAsyncDiffViewModelDelegate<C> {
  override val changesToShow = MutableStateFlow(CodeReviewAsyncDiffViewModelDelegate.ChangesState<C>())
  private val selectionListeners = CopyOnWriteArrayList<ChangesSelectionListener<C>>()

  override fun showChanges(changes: ListSelection<C>, scrollRequest: DiffViewerScrollRequest?) {
    val state = changesToShow.updateAndGet {
      if (it.selectedChanges == changes) it else CodeReviewAsyncDiffViewModelDelegate.ChangesState(changes)
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

    var state = current
    val newChanges = ListSelection.createAt(current.selectedChanges.list, newIdx)
    if (newChanges != current.selectedChanges) {
      state = current.copy(selectedChanges = newChanges)
      if (!changesToShow.compareAndSet(current, state)) {
        return
      }
      notifySelection(state.selectedChanges)
    }

    if (scrollRequest != null) {
      state.scroll(scrollRequest)
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
}

internal typealias ChangesSelectionListener<C> = (ListSelection<C>) -> Unit

internal data class ViewModelsState<CVM : Any>(
  override val selectedChanges: ListSelection<CVM>,
  override val scrollRequests: Flow<DiffViewerScrollRequest>,
) : CodeReviewDiffProcessorViewModel.State<CVM>, DiffViewerScrollRequestProducer

@ApiStatus.Internal
fun <C : Any, CVM : AsyncDiffViewModel> Flow<ChangesState<C>>.mapChangesToVms(
  createViewModel: CoroutineScope.(C) -> CVM,
): Flow<ComputedResult<CodeReviewDiffProcessorViewModel.State<CVM>>?> =
  channelFlow {
    val vmsContainer = MappingScopedItemsContainer.byEquality<C, CVM>(this) {
      createViewModel(it)
    }
    var lastList: List<C> = emptyList()
    collect { changesState ->
      ComputedResult.compute {
        if (changesState.selectedChanges.list != lastList) {
          vmsContainer.update(changesState.selectedChanges.list)
          lastList = changesState.selectedChanges.list
        }
        val vms = vmsContainer.mappedState.value
        val selectedVmIdx = changesState.selectedChanges.list.indexOf(changesState.selectedChanges.selectedItem)
        ViewModelsState(ListSelection.createAt(vms, selectedVmIdx), changesState.scrollRequests)
      }.let {
        send(it)
      }
    }
  }