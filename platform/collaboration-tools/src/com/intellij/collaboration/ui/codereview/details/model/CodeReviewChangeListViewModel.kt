// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel.SelectionRequest
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CodeReviewChangeListViewModel {
  val project: Project

  val changes: List<RefComparisonChange>

  /**
   * Flow of selection requests to be handled
   */
  val selectionRequests: SharedFlow<SelectionRequest>

  /**
   * Uni-directional state of changelist selection (changelist presentation should not collect it)
   */
  val changesSelection: StateFlow<ChangesSelection?>

  /**
   * Publish changelist selection to [changesSelection]
   */
  fun updateSelectedChanges(selection: ChangesSelection?)

  /**
   * Show diff preview for [changesSelection]
   */
  fun showDiffPreview()

  /**
   * Request standalone diff for [changesSelection]
   */
  fun showDiff()

  interface WithDetails : CodeReviewChangeListViewModel {
    /**
     * Map of additional details for changes
     */
    val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>>
  }

  sealed interface SelectionRequest {
    data object All : SelectionRequest
    data class OneChange(val change: RefComparisonChange) : SelectionRequest
  }

  companion object {
    val DATA_KEY = DataKey.create<CodeReviewChangeListViewModel>("Code.Review.Changes.List.ViewModel")
  }
}

abstract class CodeReviewChangeListViewModelBase(
  parentCs: CoroutineScope,
  protected val changeList: CodeReviewChangeList
) : CodeReviewChangeListViewModel {
  protected val cs = parentCs.childScope()

  private val _selectionRequests = MutableSharedFlow<SelectionRequest>(replay = 1)
  override val selectionRequests: SharedFlow<SelectionRequest> = _selectionRequests.asSharedFlow()

  private val _changesSelection = MutableStateFlow<ChangesSelection?>(null)
  override val changesSelection: StateFlow<ChangesSelection?> = _changesSelection.asStateFlow()

  protected val selectedCommit: String? = changeList.commitSha

  final override val changes: List<RefComparisonChange> = changeList.changes

  private val stateGuard = Mutex()

  suspend fun selectChange(change: RefComparisonChange?) {
    stateGuard.withLock {
      if (change == null) {
        _changesSelection.value = ChangesSelection.Fuzzy(changeList.changes)
        _selectionRequests.emit(SelectionRequest.All)
      }
      else {
        val currentSelection = _changesSelection.value
        if (currentSelection == null || currentSelection !is ChangesSelection.Fuzzy || !currentSelection.changes.contains(change)) {
          _changesSelection.value = ChangesSelection.Precise(changeList.changes, change)
          _selectionRequests.emit(SelectionRequest.OneChange(change))
        }
      }
    }
  }

  override fun updateSelectedChanges(selection: ChangesSelection?) {
    cs.launch {
      // do not update selection when change update is in progress
      if (!stateGuard.tryLock()) return@launch
      try {
        _changesSelection.value = selection
      }
      finally {
        stateGuard.unlock()
      }
    }
  }
}