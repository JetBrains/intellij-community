package com.intellij.database.run.ui.table

import org.jetbrains.annotations.ApiStatus
import javax.swing.table.TableColumn

/** Which of the view's columns are pinned, by model index, as the view last set the pins. Columns and slices ask it and hold no pins. */
@ApiStatus.Internal
interface ColumnPins {
  /** Whether [column] is pinned. */
  fun isPinned(column: TableColumn): Boolean
}
