package com.intellij.notebooks.ui.editor.actions.command.mode

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

interface NotebookCellCaretTracker {
  interface CellCaretPosition

  fun saveCaretPositions(editor: Editor): List<CellCaretPosition>?

  fun restoreCaretPositions(editor: Editor, positions: List<CellCaretPosition>): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName<NotebookCellCaretTracker>("com.intellij.notebooks.ui.cellCaretTracker")

    fun getInstance(): NotebookCellCaretTracker? = EP_NAME.extensionList.firstOrNull()
  }
}