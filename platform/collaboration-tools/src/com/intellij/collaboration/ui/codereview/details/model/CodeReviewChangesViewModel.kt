// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import kotlinx.coroutines.flow.*

interface CodeReviewChangesViewModel<T> {
  val reviewCommits: SharedFlow<List<T>>
  val selectedCommit: SharedFlow<T?>

  /** `-1` for "all commits" mode */
  val selectedCommitIndex: SharedFlow<Int>

  fun selectCommit(index: Int)

  fun selectNextCommit()

  fun selectPreviousCommit()

  fun commitHash(commit: T): String
}

abstract class CodeReviewChangesViewModelBase<T> : CodeReviewChangesViewModel<T> {
  abstract override val reviewCommits: StateFlow<List<T>>

  private val _selectedCommitState: MutableStateFlow<T?> = MutableStateFlow(null)
  override val selectedCommit: SharedFlow<T?> = _selectedCommitState.asSharedFlow()

  protected val _selectedCommitIndexState: MutableStateFlow<Int> = MutableStateFlow(-1)
  override val selectedCommitIndex: SharedFlow<Int> = _selectedCommitIndexState.asSharedFlow()

  override fun selectCommit(index: Int) {
    _selectedCommitState.value = reviewCommits.value.getOrNull(index)
    _selectedCommitIndexState.value = index
  }

  override fun selectNextCommit() {
    _selectedCommitIndexState.value++
    _selectedCommitState.value = reviewCommits.value[_selectedCommitIndexState.value]
  }

  override fun selectPreviousCommit() {
    _selectedCommitIndexState.value--
    _selectedCommitState.value = reviewCommits.value[_selectedCommitIndexState.value]
  }
}