package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus
import javax.swing.table.TableColumn

/** Tells a column of the view, or a slice of the view's columns, whether the column is pinned, so that it keeps no pin state. */
@ApiStatus.Internal
interface ColumnPins {
  /** Answers from the pins the view last gave, by the column's model index; the column doesn't know the grid. */
  fun isPinned(column: TableColumn): Boolean
}
