// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.JBUI

object DefaultNotebookEditorAppearanceSizes : NotebookEditorAppearanceSizes {
  // TODO it's hardcoded, but it should be equal to distance between a folding line and an editor.
  override val codeCellLeftLinePadding: Int = JBUI.scale(5)

  override val commandModeCellLeftLineWidth: Int = JBUI.scale(4)
  override val editModeCellLeftLineWidth: Int = JBUI.scale(2)

  override val innerCellToolbarHeight: Int = JBUI.scale(24)
  override val distanceBetweenCells: Int = JBUI.scale(16)
  override val cellBorderHeight: Int = JBUI.scale(16)
  override val aboveFirstCellDelimiterHeight: Int = JBUI.scale(42)
  override val spacerHeight: Int = JBUI.scale(cellBorderHeight / 2)
  override val spaceBelowCellToolbar: Int = JBUI.scale(4)
  override val cellToolbarTotalHeight: Int = JBUI.scale(innerCellToolbarHeight + spaceBelowCellToolbar)

  override fun getCellLeftLineWidth(editor: Editor): Int = editModeCellLeftLineWidth
  override fun getCellLeftLineHoverWidth(): Int = commandModeCellLeftLineWidth

  override fun getLeftBorderWidth(): Int =
    Integer.max(commandModeCellLeftLineWidth, editModeCellLeftLineWidth) + codeCellLeftLinePadding
}