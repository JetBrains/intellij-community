package com.intellij.database.datagrid

/** Hears of each change of a result view's selection of rows and columns. */
fun interface ResultViewSelectionListener {
  /** [isAdjusting]: more changes of the same gesture follow, as while the user drags across cells. */
  fun selectionChanged(isAdjusting: Boolean)
}
