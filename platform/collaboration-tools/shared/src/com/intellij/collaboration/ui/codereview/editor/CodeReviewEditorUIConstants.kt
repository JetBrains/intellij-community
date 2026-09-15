// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus
import kotlin.math.roundToInt

/**
 * Sizing constants shared between the review-in-editor renderers and the chat item UI.
 *
 * They live here rather than in `CodeReviewChatItemUIUtil` because the gutter and inlay renderers must be loadable on
 * a split-mode frontend, while the chat item UI is not.
 */
@ApiStatus.Internal
object CodeReviewEditorUIConstants {

  /**
   * Maximum width for textual content for it to be readable
   * Equals to 42em
   */
  val TEXT_CONTENT_WIDTH: Int
    get() = (JBUIScale.DEF_SYSTEM_FONT_SIZE * 42).roundToInt()

  const val THREAD_TOP_MARGIN: Int = 8
}
