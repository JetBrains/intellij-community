// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.endInlay.addToolbar

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookBelowLastCellPanel
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.context.NotebookDataContext
import com.intellij.notebooks.visualization.ui.addComponentInlay
import com.intellij.notebooks.visualization.ui.cellsDnD.DropHighlightable
import com.intellij.notebooks.visualization.ui.endInlay.EditorNotebookEndInlay
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.util.Disposer

class EditorNotebookEndAddToolbar(val inlayManager: NotebookCellInlayManager) : EditorNotebookEndInlay, DropHighlightable {
  private val editor = inlayManager.editor

  private val belowLastCellPanel: NotebookBelowLastCellPanel = NotebookBelowLastCellPanel(editor)

  init {
    // PY-77218
    editor.addComponentInlay(
      UiDataProvider.wrapComponent(belowLastCellPanel) { sink ->
        sink[NotebookDataContext.NOTEBOOK_CELL_LINES_INTERVAL] = inlayManager.notebook.cells.lastOrNull()?.interval
        sink[NotebookDataContext.SHOW_TEXT] = true
        sink[NotebookDataContext.NOTEBOOK_CELL_INSERT_ABOVE] = false
      },
      isRelatedToPrecedingText = true,
      showAbove = false,
      priority = editor.notebookAppearance.jupyterBelowLastCellInlayPriority,
      offset = editor.document.getLineEndOffset((editor.document.lineCount - 1).coerceAtLeast(0))
    ).also {
      Disposer.register(this, it)
    }
  }

  override fun addDropHighlight() {
    belowLastCellPanel.addDropHighlight()
  }

  override fun removeDropHighlight() {
    belowLastCellPanel.removeDropHighlight()
  }
}