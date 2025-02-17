package com.intellij.notebooks.visualization

import com.intellij.notebooks.visualization.ui.EditorCell

interface EditorNotebookExtension {
  fun onCellCreated(cell: EditorCell) {}
}
