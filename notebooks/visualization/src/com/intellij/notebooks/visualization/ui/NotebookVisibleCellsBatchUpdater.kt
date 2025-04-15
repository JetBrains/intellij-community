// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.FlowPreview
import java.awt.Point

@OptIn(FlowPreview::class)
class NotebookVisibleCellsBatchUpdater(
  private val editor: EditorImpl
) : Disposable {

  init {
    JupyterBoundsChangeHandler.get(editor).subscribe(this, object : JupyterBoundsChangeListener {
      override fun boundsChanged() {
        bulkUpdateFrames()
      }
    })
  }

  private fun bulkUpdateFrames() {
    val inlayManager = NotebookCellInlayManager.get(editor) ?: return
    val visibleArea = editor.scrollingModel.visibleArea

    val firstVisibleLine = editor.xyToLogicalPosition(Point(0, visibleArea.y)).line
    val lastVisibleLine = editor.xyToLogicalPosition(Point(0, visibleArea.y + visibleArea.height)).line

    for (cell in inlayManager.cells) {
      val firstLine = cell.interval.firstContentLine
      val lastLine = cell.interval.lastContentLine

      // The cell is above the visible area, go to next
      if (lastLine < firstVisibleLine) continue
      // This cell is below the visible area, stop iteration - they all are for sure outside the visible area
      if (firstLine > lastVisibleLine) break

      updateCell(cell)
    }
  }

  private fun updateCell(cell: EditorCell) {
    cell.view?.cellFrameManager?.redrawBorders()
    cell.view?.input?.cellActionsToolbar?.updateToolbarPosition()
  }
  override fun dispose() {

  }

  companion object {
    private val INSTANCE_KEY = Key.create<NotebookVisibleCellsBatchUpdater>("EDITOR_CELL_FRAME_UPDATER_KEY")

    fun install(editor: EditorImpl) {
      val updater = NotebookVisibleCellsBatchUpdater(editor)
      editor.putUserData(INSTANCE_KEY, updater)
      Disposer.register(editor.disposable, updater)
    }
  }
}