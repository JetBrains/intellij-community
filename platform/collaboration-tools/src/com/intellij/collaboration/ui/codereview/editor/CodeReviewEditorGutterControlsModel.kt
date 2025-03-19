// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.StateFlow

/**
 * A UI model for an editor gutter with review controls
 * This model should exist in the same scope as the gutter
 * One model - one gutter
 */
interface CodeReviewEditorGutterControlsModel : CodeReviewCommentableEditorModel {
  val gutterControlsState: StateFlow<ControlsState?>

  @RequiresEdt
  override fun canCreateComment(lineIdx: Int): Boolean = gutterControlsState.value?.isLineCommentable(lineIdx) == true

  @RequiresEdt
  fun toggleComments(lineIdx: Int)

  interface ControlsState {
    @get:RequiresEdt
    val linesWithComments: Set<Int>

    @get:RequiresEdt
    val linesWithNewComments: Set<Int>
      get() = setOf()

    @RequiresEdt
    fun isLineCommentable(lineIdx: Int): Boolean
  }

  interface WithMultilineComments : CodeReviewCommentableEditorModel.WithMultilineComments
}