package com.intellij.database.run.ui.table

import com.intellij.openapi.util.Ref

/**
 * The value of the open multi-cell edit, which every selected cell shows while the editor is open. Both tables of a view share it. The view
 * sets it when the grid reports an edited value, and the table whose editor closes clears it.
 */
internal class MultiCellEditValue {
  /** The edited value, or null while no open edit has reported one. */
  var value: Ref<Any?>? = null
}
