// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

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

  fun requestNewComment(lineIdx: Int)
  fun toggleComments(lineIdx: Int)

  interface ControlsState {
    val linesWithComments: Set<Int>
    fun isLineCommentable(lineIdx: Int): Boolean
  }
}