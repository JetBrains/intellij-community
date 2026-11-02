package com.intellij.database.run.ui.table

/** Hears whether a table's column header background is transparent, that is, the same as the table's background. */
fun interface ColumnHeaderBackgroundListener {
  fun transparencyChanged(isTransparent: Boolean)
}
