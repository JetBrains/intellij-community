package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.plugins.notebooks.ui.isFoldingEnabledKey

class EditorCellFolding(editor: EditorEx, toggleListener: () -> Unit) {

  private val foldingBar: EditorCellFoldingBar?

  init {
    val isFoldingEnabled = editor.getUserData(isFoldingEnabledKey) ?: false
    if (isFoldingEnabled) {
      foldingBar = EditorCellFoldingBar(editor, toggleListener)
    }
    else {
      foldingBar = null
    }
  }

  fun dispose() {
    foldingBar?.dispose()
  }

  fun hide() {
    foldingBar?.visible = false
  }

  fun show() {
    foldingBar?.visible = true
  }

  fun updatePosition(y: Int, height: Int) {
    foldingBar?.setLocation(y, height)
  }

  fun updateSelection(value: Boolean) {
    foldingBar?.selected = value
  }

}
