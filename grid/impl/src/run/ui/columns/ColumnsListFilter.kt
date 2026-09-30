package com.intellij.database.run.ui.columns

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus

/**
 * The filter of the column list. It reduces the rows of the popup and never changes the grid.
 *
 * The filter is immutable. A change makes a new filter.
 */
@ApiStatus.Internal
data class ColumnsListFilter(val text: String = "") {
  /** True when the filter keeps every row. */
  val isEmpty: Boolean get() = text.isEmpty()

  /** The name holds the text, whatever the case. The text is never read as a pattern. */
  fun matches(item: ColumnsListItem): Boolean =
    text.isEmpty() || StringUtil.containsIgnoreCase(item.name, text)
}
