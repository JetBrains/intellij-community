package com.intellij.notebooks.visualization

import com.intellij.notebooks.visualization.ui.EditorCell

interface CellExtensionFactory {
  fun onCellCreated(cell: EditorCell)
}
