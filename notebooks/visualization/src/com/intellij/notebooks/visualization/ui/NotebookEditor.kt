package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.notebooks.visualization.NotebookCellLines

interface NotebookEditor {
  val mouseOverCell: EditorCellView?
  fun inlayClicked(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean, mouseButton: Int)
}

internal val notebookEditorKey = Key.create<NotebookEditor>(NotebookEditor::class.java.name)

val Editor.notebookEditor: NotebookEditor
  get() = notebookEditorKey.get(this)