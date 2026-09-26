// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Insets

internal object CombinedDiffUI {
  val MAIN_HEADER_BACKGROUND: Color
    get() = UIUtil.getPanelBackground()

  val MAIN_HEADER_INSETS: Insets
    get() = JBUI.CurrentTheme.VersionControl.CombinedDiff.mainToolbarInsets()

  val BLOCK_HEADER_BACKGROUND: Color = JBColor.lazy {
    EditorColorsManager.getInstance().globalScheme.defaultBackground
  }

  val BLOCK_HEADER_INSETS: Insets
    get() = JBUI.CurrentTheme.VersionControl.CombinedDiff.fileToolbarInsets()

  val LOADING_BLOCK_BACKGROUND: Color
    get() = BLOCK_HEADER_BACKGROUND

  val EDITOR_BORDER_COLOR: Color = JBColor.lazy {
    JBUI.CurrentTheme.Editor.BORDER_COLOR
  }

  val LOADING_BLOCK_PROGRESS_DELAY = 200

  val GAP_BETWEEN_BLOCKS: Int
    get() = JBUI.CurrentTheme.VersionControl.CombinedDiff.gapBetweenBlocks()

  val LEFT_RIGHT_INSET: Int
    get() = JBUI.CurrentTheme.VersionControl.CombinedDiff.leftRightBlockInset()

  val BLOCK_ARC: Int
    get() = 12

  private val BLOCK_BORDER_SELECTED_ACTIVE_COLOR: Color = JBColor.lazy { JBColor.namedColor("CombinedDiff.BlockBorder.selectedActiveColor") }
  private val BLOCK_BORDER_SELECTED_INACTIVE_COLOR: Color = JBColor.lazy { JBColor.namedColor("CombinedDiff.BlockBorder.selectedInactiveColor") }

  fun getBlockBorderColor(selected: Boolean, focused: Boolean): Color {
    return when {
      selected && focused -> BLOCK_BORDER_SELECTED_ACTIVE_COLOR
      selected && !focused -> BLOCK_BORDER_SELECTED_INACTIVE_COLOR
      else -> EDITOR_BORDER_COLOR
    }
  }
}
