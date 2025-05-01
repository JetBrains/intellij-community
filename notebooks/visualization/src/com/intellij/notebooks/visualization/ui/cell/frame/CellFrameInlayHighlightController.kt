// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.frame

import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.controllers.selfUpdate.common.NotebookCellSelfHighlighterController
import com.intellij.notebooks.visualization.controllers.selfUpdate.common.NotebookCellSelfInlayController
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.openapi.editor.ex.RangeHighlighterEx

class CellFrameInlayHighlightController(
  editorCell: EditorCell,
  val isAbove: Boolean,
  val attachedInlayController: NotebookCellSelfInlayController,
) : NotebookCellSelfHighlighterController(editorCell) {

  init {
    editorCell.cellFrameManager?.state?.bind(this) { state ->
      if (state.isVisible) {
        checkAndRebuildInlays()
      }
      else {
        disposeHighlighter()
      }
    }
  }

  override fun getHighlighterLayer(): Int = editor.notebookAppearance.cellBorderHighlightLayer

  override fun createLineMarkerRender(rangeHighlighter: RangeHighlighterEx): CellBorderGutterCornerRender =
    CellBorderGutterCornerRender(isAbove, attachedInlayController)
}