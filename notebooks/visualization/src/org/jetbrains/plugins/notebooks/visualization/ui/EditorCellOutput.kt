package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayController

internal class EditorCellOutput(editor: EditorEx, private val outputController: NotebookOutputInlayController) {

  private val folding = EditorCellFolding(editor) { outputController.toggle() }.also {
    it.bindTo(outputController)
  }

  fun updatePositions() {
    folding.updatePosition()
  }

  fun dispose() {
    folding.dispose()
  }

}