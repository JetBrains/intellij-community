package com.intellij.notebooks.ui.editor.actions.command.mode

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

interface NotebookCellCaretTracker {
  interface CellId
  interface CellCaretPosition
  interface CellCaretSnapshot {
    val cellId: CellId
    val positions: List<CellCaretPosition>
  }

  fun saveCaretPositions(editor: Editor): CellCaretSnapshot?

  fun getCurrentCellId(editor: Editor): CellId?

  fun restoreCaretPositions(editor: Editor, positions: List<CellCaretPosition>): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName<NotebookCellCaretTracker>("com.intellij.notebooks.ui.cellCaretTracker")

    fun getInstance(): NotebookCellCaretTracker? = EP_NAME.extensionList.firstOrNull()
  }
}