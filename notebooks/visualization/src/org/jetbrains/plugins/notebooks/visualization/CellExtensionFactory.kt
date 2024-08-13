package org.jetbrains.plugins.notebooks.visualization

import org.jetbrains.plugins.notebooks.visualization.ui.EditorCell

interface CellExtensionFactory {
  fun onCellCreated(cell: EditorCell)
}
