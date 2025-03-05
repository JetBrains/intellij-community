// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.jupyterToolbars

import com.intellij.notebooks.visualization.ui.EditorCellInput
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key

class NotebookCellActionsToolbarStateTracker : Disposable {
  var lastSelectedCell: EditorCellInput? = null

  fun updateLastSelectedCell(cell: EditorCellInput) {
    if (lastSelectedCell == cell) return
    lastSelectedCell?.cellActionsToolbar?.hideToolbar()
    lastSelectedCell = cell
  }

  override fun dispose() {
    lastSelectedCell?.cellActionsToolbar?.hideToolbar()
    lastSelectedCell = null
  }

  companion object {
    private val CELL_ACTIONS_TOOLBAR_STATE_TRACKER = Key.create<NotebookCellActionsToolbarStateTracker>("CellActionsToolbarStateTracker")

    fun install(editor: EditorImpl) {
      val tracker = NotebookCellActionsToolbarStateTracker()
      editor.putUserData(CELL_ACTIONS_TOOLBAR_STATE_TRACKER, tracker)
      Disposer.register(editor.disposable, tracker)
    }

    fun get(editor: EditorImpl): NotebookCellActionsToolbarStateTracker? = editor.getUserData(CELL_ACTIONS_TOOLBAR_STATE_TRACKER)
  }

}