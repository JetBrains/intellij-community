// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.diff.util.LineRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * A UI model for an editor gutter with review controls
 * This model should exist in the same scope as the gutter
 * One model - one gutter
 */
@ApiStatus.Internal
interface CodeReviewEditorGutterControlsModel {
  val gutterControlsState: StateFlow<ControlsState?>

  @RequiresEdt
  fun requestNewComment(lineIdx: Int)

  @RequiresEdt
  fun toggleComments(lineIdx: Int)

  interface ControlsState {
    @get:RequiresEdt
    val linesWithComments: Set<Int>

    @RequiresEdt
    fun isLineCommentable(lineIdx: Int): Boolean
  }

  interface WithMultilineComments : CodeReviewEditorGutterControlsModel {
    @RequiresEdt
    fun canCreateComment(lineRange: LineRange): Boolean

    @RequiresEdt
    fun requestNewComment(lineRange: LineRange)
  }
}