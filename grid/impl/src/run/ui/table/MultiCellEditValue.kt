package com.intellij.database.run.ui.table

import com.intellij.openapi.util.Ref

/**
 * The value of the open multi-cell edit, which the editing table shows in each selected cell. One instance serves both tables, so a write
 * reaches whichever table edits. The value sits in a [Ref] so that a null value differs from no value.
 */
internal class MultiCellEditValue {
  /** The edited value, or null while no open edit has reported one. */
  var value: Ref<Any?>? = null
}
