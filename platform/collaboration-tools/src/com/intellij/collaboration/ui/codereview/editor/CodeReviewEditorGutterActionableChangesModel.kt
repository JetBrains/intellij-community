// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.diff.util.LineRange
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * A UI model for an editor with gutter changes highlighting and a popup with actions for changes
 * This model should exist in the same scope as the editor
 * One model - one editor
 */
@ApiStatus.Internal
interface CodeReviewEditorGutterActionableChangesModel : CodeReviewEditorGutterChangesModel {

  @get:RequiresEdt
  var highlightDiffRanges: Boolean

  @RequiresEdt
  fun getOriginalContent(lines: LineRange): String?

  @RequiresEdt
  fun showDiff(lineIdx: Int?)

  @RequiresEdt
  fun addDiffHighlightListener(disposable: Disposable, listener: () -> Unit)
}