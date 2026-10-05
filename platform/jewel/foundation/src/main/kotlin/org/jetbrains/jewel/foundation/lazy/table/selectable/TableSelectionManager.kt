// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table.selectable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.jewel.foundation.lazy.selectable.SelectionManager

/**
 * A selection manager for table components that handles cell, row, or column selection.
 *
 * Extends [SelectionManager] to provide table-specific selection behavior by supporting separate column and row keys.
 * Implementations can manage different selection modes such as single cell, single row, or single column selection.
 *
 * @see SingleCellSelectionManager
 * @see SingleRowSelectionManager
 * @see SingleColumnSelectionManager
 */
public interface TableSelectionManager : SelectionManager {
    /**
     * A set of selected table items, where each item is represented as a pair of keys.
     *
     * The first element of the pair represents the column key, and the second element represents the row key. This set
     * contains only the currently selected items in the table, allowing efficient access and management of selection
     * state.
     *
     * Notes:
     * - When a Cell is selected, both column (.first) and row (.second) keys are included in the set.
     * - When a Row is selected, only the row key (.second) is included in the set.
     * - When a Column is selected, only the column key (.first) is included in the set.
     */
    override val selectedItems: Set<Pair<Any?, Any?>>

    /**
     * Checks if a cell at the given column and row is selectable.
     *
     * @param columnKey The key identifying the column, or null if not applicable
     * @param rowKey The key identifying the row, or null if not applicable
     * @return true if the cell is selectable, false otherwise
     */
    public fun isSelectable(columnKey: Any?, rowKey: Any?): Boolean

    /**
     * Checks if an item is selectable by unpacking a [Pair] into column and row keys.
     *
     * If [itemKey] is a [Pair], delegates to [isSelectable] with the pair's first element as the column key and second
     * element as the row key. Otherwise, returns true.
     *
     * @param itemKey The item key to check, expected to be a Pair<columnKey, rowKey> or null
     * @return true if the item is selectable, false otherwise
     */
    override fun isSelectable(itemKey: Any?): Boolean {
        if (itemKey is Pair<*, *>) {
            return isSelectable(itemKey.first, itemKey.second)
        }

        return true
    }

    /**
     * Checks if a cell at the given column and row is currently selected.
     *
     * @param columnKey The key identifying the column, or null if not applicable
     * @param rowKey The key identifying the row, or null if not applicable
     * @return true if the cell is selected, false otherwise
     */
    public fun isSelected(columnKey: Any?, rowKey: Any?): Boolean

    override fun isSelected(itemKey: Any?): Boolean {
        if (itemKey is Pair<*, *>) {
            return isSelected(itemKey.first, itemKey.second)
        }

        return false
    }
}

/**
 * Creates and remembers a [SingleCellSelectionManager] that manages single cell selection in a table.
 *
 * Allows only one cell (identified by both column and row keys) to be selected at a time. When a new cell is selected,
 * the previous selection is cleared.
 *
 * @param initialColumnKey The column key of the initially selected cell, or null for no initial selection
 * @param initialRowKey The row key of the initially selected cell, or null for no initial selection
 * @return A [TableSelectionManager] that manages single cell selection
 * @see SingleCellSelectionManager
 */
@Composable
public fun rememberSingleCellSelectionManager(
    initialColumnKey: Any? = null,
    initialRowKey: Any? = null,
): TableSelectionManager = remember { SingleCellSelectionManager(initialColumnKey, initialRowKey) }

/**
 * Creates and remembers a [SingleRowSelectionManager] that manages single row selection in a table.
 *
 * Allows only one row to be selected at a time, regardless of which column is interacted with. When a new row is
 * selected, the previous selection is cleared. All cells in the selected row are considered selected.
 *
 * @param initialRowKey The row key of the initially selected row, or null for no initial selection
 * @return A [TableSelectionManager] that manages single row selection
 * @see SingleRowSelectionManager
 */
@Composable
public fun rememberSingleRowSelectionManager(initialRowKey: Any? = null): TableSelectionManager = remember {
    SingleRowSelectionManager(initialRowKey)
}

/**
 * Creates and remembers a [SingleColumnSelectionManager] that manages single column selection in a table.
 *
 * Allows only one column to be selected at a time, regardless of which row is interacted with. When a new column is
 * selected, the previous selection is cleared. All cells in the selected column are considered selected.
 *
 * @param initialColumnKey The column key of the initially selected column, or null for no initial selection
 * @return A [TableSelectionManager] that manages single column selection
 * @see SingleColumnSelectionManager
 */
@Composable
public fun rememberSingleColumnSelectionManager(initialColumnKey: Any? = null): TableSelectionManager = remember {
    SingleColumnSelectionManager(initialColumnKey)
}
