// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

interface CodeReviewChangesViewModel<T> {
  val reviewCommits: StateFlow<List<T>>
  val selectedCommit: Flow<T?>
  val selectedCommitIndex: Flow<Int>

  fun selectCommit(commit: T?)

  fun selectAllCommits()

  fun selectNextCommit()

  fun selectPreviousCommit()

  fun commitHash(commit: T): String
}

abstract class CodeReviewChangesViewModelBase<T> : CodeReviewChangesViewModel<T> {
  private val _selectedCommitState: MutableStateFlow<T?> = MutableStateFlow(null)
  override val selectedCommit: Flow<T?> = _selectedCommitState.asSharedFlow()

  private val _selectedCommitIndexState: MutableStateFlow<Int> = MutableStateFlow(0)
  override val selectedCommitIndex: Flow<Int> = _selectedCommitIndexState.asSharedFlow()

  override fun selectCommit(commit: T?) {
    _selectedCommitState.value = commit
    _selectedCommitIndexState.value = reviewCommits.value.indexOf(commit)
  }

  override fun selectAllCommits() {
    _selectedCommitState.value = null
    _selectedCommitIndexState.value = 0
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