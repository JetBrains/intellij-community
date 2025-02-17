// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.openapi.ListSelection
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.CopyOnWriteArrayList

internal interface CodeReviewAsyncDiffViewModelDelegate<C : Any> {
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
}

internal typealias ChangesSelectionListener<C> = (ListSelection<C>) -> Unit
