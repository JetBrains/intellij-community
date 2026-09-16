package com.intellij.database.run.ui

/** A grid that keeps a column order of its own, which a view asks it to apply again after rebuilding its columns. */
interface ColumnOrderRestorer {
  fun restoreColumnsOrder()
}
