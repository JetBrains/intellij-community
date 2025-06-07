package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.notebooks.visualization.ui.EditorCell

data class CellDropEvent(
  val sourceCell: EditorCell,
  val targetCell: CellDropTarget,
)
