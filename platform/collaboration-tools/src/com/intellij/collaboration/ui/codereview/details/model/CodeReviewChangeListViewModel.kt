// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel.SelectionRequest
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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

  @ApiStatus.Experimental
  interface WithGrouping : CodeReviewChangeListViewModel {
    /**
     * A set of enabled grouping policies
     */
    val grouping: StateFlow<Set<String>>

    fun setGrouping(grouping: Collection<String>)
  }

  @ApiStatus.Experimental
  interface WithViewedState : WithDetails {
    @RequiresEdt
    fun setViewedState(changes: Iterable<RefComparisonChange>, viewed: Boolean)
  }

  sealed interface SelectionRequest {
    data object All : SelectionRequest
    data class OneChange(val change: RefComparisonChange) : SelectionRequest
  }

  companion object {
    val DATA_KEY: DataKey<CodeReviewChangeListViewModel> = DataKey.create("Code.Review.Changes.List.ViewModel")
  }
}

fun CodeReviewChangeListViewModel.WithDetails.isViewedStateForAllChanges(changes: Iterable<RefComparisonChange>, viewed: Boolean): Boolean =
  changes.all { detailsByChange.value[it]?.isRead == viewed }

@ApiStatus.Internal
abstract class CodeReviewChangeListViewModelBase(
  parentCs: CoroutineScope,
  protected val changeList: CodeReviewChangeList
) : CodeReviewChangeListViewModel {
  protected val cs = parentCs.childScope()

  private val _selectionRequests = MutableSharedFlow<SelectionRequest>(replay = 1)
  override val selectionRequests: SharedFlow<SelectionRequest> = _selectionRequests.asSharedFlow()

  private val _changesSelection = MutableStateFlow<ChangesSelection?>(null)
  override val changesSelection: StateFlow<ChangesSelection?> = _changesSelection.asStateFlow()
  private val selectionMulticaster = EventDispatcher.create(SimpleEventListener::class.java)

  protected val selectedCommit: String? = changeList.commitSha

  final override val changes: List<RefComparisonChange> = changeList.changes

  private val stateGuard = ReentrantLock()

  fun selectChange(change: RefComparisonChange?) {
    stateGuard.withLock {
      if (change == null) {
        _changesSelection.value = ChangesSelection.Fuzzy(changeList.changes)
        _selectionRequests.tryEmit(SelectionRequest.All)
      }
      else {
        if (!changeList.changes.contains(change)) return
        val currentSelection = _changesSelection.value
        if (currentSelection == null || currentSelection !is ChangesSelection.Fuzzy || !currentSelection.changes.contains(change)) {
          _changesSelection.value = ChangesSelection.Precise(changeList.changes, change)
          _selectionRequests.tryEmit(SelectionRequest.OneChange(change))
        }
        else {
          // for some reason this if is considered an expression, so we need this to avoid compiler complaints
        }
      }
    }
  }

  override fun updateSelectedChanges(selection: ChangesSelection?) {
    // do not update selection when change update is in progress
    if (!stateGuard.tryLock()) return
    try {
      _changesSelection.value = selection
      selectionMulticaster.multicaster.eventOccurred()
    }
    finally {
      stateGuard.unlock()
    }
  }

  /**
   * Listener invoked SYNCHRONOUSLY when selection is changed
   */
  suspend fun handleSelection(listener: (ChangesSelection?) -> Unit): Nothing {
    val simpleListener = SimpleEventListener { listener(changesSelection.value) }
    try {
      selectionMulticaster.addListener(simpleListener)
      listener(changesSelection.value)
      awaitCancellation()
    }
    finally {
      selectionMulticaster.removeListener(simpleListener)
    }
  }
}