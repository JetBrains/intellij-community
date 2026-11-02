package com.intellij.database.run.ui.table

/** Hears the start and the end of each change of the view's column model: an add, a removal or a move of a column. */
internal interface ColumnModelChangeListener {
  /** The column model is about to change. */
  fun changeStarted()

  /** The change is over, and every listener of the column model has heard of it. Also called when the change failed. */
  fun changeFinished()
}
