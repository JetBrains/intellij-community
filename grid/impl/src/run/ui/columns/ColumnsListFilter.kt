package com.intellij.database.run.ui.columns

import com.intellij.database.datagrid.GridColumnTrait
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus

/**
 * The filter of the column list. It reduces the rows of the popup and never changes the grid.
 *
 * The filter is immutable. A change makes a new filter.
 */
@ApiStatus.Internal
data class ColumnsListFilter(
  val text: String = "",
  val traits: Set<GridColumnTrait> = emptySet(),
  val visibility: Visibility = Visibility.ALL,
) {
  /** Which columns the filter keeps, by the state the grid shows them in. */
  enum class Visibility { ALL, SHOWN, HIDDEN }

  /** True when the filter keeps every row. */
  val isEmpty: Boolean get() = text.isEmpty() && traits.isEmpty() && visibility == Visibility.ALL

  fun matches(item: ColumnsListItem): Boolean =
    matchesVisibility(item) && matchesTraits(item) && matchesText(item.name)

  private fun matchesVisibility(item: ColumnsListItem): Boolean = when (visibility) {
    Visibility.ALL -> true
    Visibility.SHOWN -> item.visible
    Visibility.HIDDEN -> !item.visible
  }

  private fun matchesTraits(item: ColumnsListItem): Boolean =
    traits.isEmpty() || traits.any { it in item.traits }

  /** The name holds the text, whatever the case. The text is never read as a pattern. */
  private fun matchesText(name: String): Boolean =
    text.isEmpty() || StringUtil.containsIgnoreCase(name, text)
}
