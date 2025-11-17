// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.diff.util.Side
import kotlinx.coroutines.flow.StateFlow

/**
 * A UI model for an editor with code review inlays
 * This model should exist in the same scope as the gutter
 * One model - one gutter
 */
interface CodeReviewEditorInlaysModel<I : CodeReviewInlayModel> {
  val inlays: StateFlow<Collection<I>>
}

interface CodeReviewInlayModel : EditorMappedViewModel {
  val key: Any
  override val line: StateFlow<Int?>
  override val isVisible: StateFlow<Boolean>
}

interface CodeReviewInlayWithOutlineModel {
  val shouldShowOutline: StateFlow<Boolean>
  val range: StateFlow<Pair<Side, IntRange>?>

  fun setFocused(isFocused: Boolean)
  fun setDimmed(isDimmed: Boolean)
  val isFocused: StateFlow<Boolean>
  val isDimmed: StateFlow<Boolean>

  fun showOutline(isHovered: Boolean)
}

interface ResizableInlayModel : CodeReviewInlayWithOutlineModel {
  fun setRange(range: Pair<Side, IntRange>?)
  fun requestFocus()
  fun setHidden(hidden: Boolean)
  val isHidden: StateFlow<Boolean>
}
