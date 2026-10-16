package com.intellij.database.run.ui.table

import java.awt.Graphics
import java.awt.Rectangle

/** Paints an addition to a column header. */
fun interface ColumnHeaderPainter {
  /** [modelColumn] is the model index of the table column, which is a grid row when the view is transposed. */
  fun paint(graphics: Graphics, nameBounds: Rectangle, modelColumn: Int)
}
