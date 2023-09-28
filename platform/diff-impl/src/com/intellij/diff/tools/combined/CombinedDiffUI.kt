// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Insets

internal object CombinedDiffUI {
  val MAIN_HEADER_BACKGROUND: Color = JBColor.lazy {
    if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.EditorTabs.background() else UIUtil.getPanelBackground()
  }

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
}