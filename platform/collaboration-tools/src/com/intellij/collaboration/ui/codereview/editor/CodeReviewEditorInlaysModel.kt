// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.diff.util.LineRange
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

  interface Ranged : CodeReviewInlayModel {
    val range: StateFlow<LineRange?>

    interface Adjustable : Ranged {
      val adjustmentDisabledReason: StateFlow<AdjustmentDisabledReason?>
      fun adjustRange(newStart: Int? = null, newEnd: Int? = null)

      enum class AdjustmentDisabledReason {
        SUGGESTED_CHANGE
      }
    }
  }
}