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