package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.util.Key

interface NotebookEditor {
  fun inlayClicked(clickedCell: NotebookCellLines.Interval, ctrlPressed: Boolean, shiftPressed: Boolean, mouseButton: Int)
  val editorPositionKeeper: NotebookPositionKeeper

  /** Updated by JupyterAboveCellToolbarManager. When set, we are hiding the cell action toolbar in the top right corner. */
  val cellAddToolbarShown: AtomicProperty<Boolean>

  /** Updated by NotebookEditorCellHoverDetector. Used to show cell action toolbar and cell selection marker in the gutter. */
  val hoveredCell: AtomicProperty<EditorCell?>

  /** Updated by JupyterAIManyCellsCodeGenerationInteraction. */
  val singleFileDiffMode: AtomicProperty<Boolean>
}

internal val NOTEBOOK_EDITOR_KEY = Key.create<NotebookEditor>(NotebookEditor::class.java.name)

val Editor.notebookEditor: NotebookEditor
  get() = notebookEditorOrNull!!

val Editor.notebookEditorOrNull: NotebookEditor?
  get() = NOTEBOOK_EDITOR_KEY.get(this)