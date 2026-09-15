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

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun canCreateComment(lineIdx: Int): Boolean = gutterControlsState.value?.isLineCommentable(lineIdx) == true

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun toggleComments(lineIdx: Int)

  interface ControlsState {
    @get:RequiresEdt(generateAssertion = false /* IJPL-115548 */)
    val linesWithComments: Set<Int>

    @get:RequiresEdt(generateAssertion = false /* IJPL-115548 */)
    val linesWithNewComments: Set<Int>
      get() = setOf()

    @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
    fun isLineCommentable(lineIdx: Int): Boolean
  }
}