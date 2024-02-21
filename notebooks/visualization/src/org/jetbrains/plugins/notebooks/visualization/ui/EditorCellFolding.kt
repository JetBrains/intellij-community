package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.plugins.notebooks.ui.isFoldingEnabledKey
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer

class EditorCellFolding(editor: EditorEx, toggleListener: () -> Unit) {

  private val foldingBar: EditorCellFoldingBar?

  private val binder: EditorCellFoldingBarLocationBinder?

  init {
    val isFoldingEnabled = editor.getUserData(isFoldingEnabledKey) ?: false
    if (isFoldingEnabled) {
      foldingBar = EditorCellFoldingBar(editor, toggleListener)
      binder = EditorCellFoldingBarLocationBinder(editor, foldingBar)
    }
    else {
      foldingBar = null
      binder = null
    }
  }

  fun bindTo(controller: NotebookCellInlayController?) {
    binder?.bindTo(controller)
  }

  fun dispose() {
    binder?.dispose()
    foldingBar?.dispose()
  }

  fun bindTo(interval: NotebookIntervalPointer) {
    binder?.bindTo(interval)
  }

  fun updatePosition() {
    binder?.updatePositions()
  }

}
