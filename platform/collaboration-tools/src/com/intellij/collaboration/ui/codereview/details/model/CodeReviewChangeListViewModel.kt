// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel.Update
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CodeReviewChangeListViewModel {
  val project: Project

  /**
   * Flow of updates to changelist state
   */
  val updates: SharedFlow<Update>

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

  sealed class Update(val changes: List<Change>) {
    class WithSelectAll(changes: List<Change>) : Update(changes)
    class WithSelectChange(changes: List<Change>, val change: Change) : Update(changes)
  }
}

abstract class MutableCodeReviewChangeListViewModel(parentCs: CoroutineScope) : CodeReviewChangeListViewModel {
  protected val cs = parentCs.childScope()

  private val _updates = MutableSharedFlow<Update>(replay = 1)
  override val updates: SharedFlow<Update> = _updates.asSharedFlow()

  private val _changesSelection = MutableStateFlow<ChangesSelection?>(null)
  override val changesSelection: StateFlow<ChangesSelection?> = _changesSelection.asStateFlow()

  protected var selectedCommit: String? = null

  private val stateGuard = Mutex()

  fun updatesChanges(changesContainer: CodeReviewChangesContainer, commit: String?, changeToSelect: Change? = null) {
    cs.launch {
      stateGuard.withLock {
        selectedCommit = commit
        val changes = changesContainer.getChanges(commit)
        if (changeToSelect == null) {
          _changesSelection.value = ChangesSelection.Fuzzy(changes)
          _updates.emit(Update.WithSelectAll(changes))
        }
        else {
          _changesSelection.value = ChangesSelection.Precise(changes, changeToSelect)
          _updates.emit(Update.WithSelectChange(changes, changeToSelect))
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

fun CodeReviewChangeListViewModel.updateSelectedChangesFromTree(allChanges: List<Change>, tree: AsyncChangesTree) {
  var fuzzy = false
  val changes = mutableListOf<Change>()
  VcsTreeModelData.selected(tree).iterateRawNodes().forEach {
    if (it.isLeaf) {
      val change = it.userObject as? Change
      changes.add(change!!)
    }
    else {
      fuzzy = true
    }
  }
  val selection = if (changes.isEmpty()) null
  else if (fuzzy) {
    ChangesSelection.Fuzzy(changes)
  }
  else {
    ChangesSelection.Precise(allChanges, changes[0])
  }
  updateSelectedChanges(selection)
}