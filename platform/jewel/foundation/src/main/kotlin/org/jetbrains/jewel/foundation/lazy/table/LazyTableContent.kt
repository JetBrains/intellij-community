// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset

/**
 * Interface that defines the content structure and layout properties of a lazy table.
 *
 * This interface provides methods to access table dimensions, map between linear and 2D positions, retrieve keys and
 * content types for cells, obtain size constraints for rows and columns, and compose individual cell items.
 *
 * Implementations of this interface manage the table's content organization and provide the necessary information for
 * the lazy layout system to efficiently render visible cells.
 *
 * @see LazyTableScope
 * @see LazyTableLayoutScope
 */
public interface LazyTableContent {
    /** The total number of columns in the table. */
    public val columnCount: Int

    /** The total number of rows in the table. */
    public val rowCount: Int

    /**
     * Gets the unique key for a cell at the given 2D position.
     *
     * Keys are used by the lazy layout system to identify cells across recompositions and enable efficient animations
     * and state preservation.
     *
     * @param position The 2D position (column, row) of the cell.
     * @return A pair of keys (columnKey, rowKey) uniquely identifying the cell.
     */
    public fun getKey(position: IntOffset): Pair<Any, Any>

    /**
     * Gets the unique key for a cell at the given linear index.
     *
     * This is a convenience method that converts the linear index to a 2D position and retrieves the key pair.
     *
     * @param index The linear index of the cell.
     * @return A pair of keys (columnKey, rowKey) uniquely identifying the cell.
     * @see getKey
     */
    public fun getKey(index: Int): Pair<Any, Any>

    /**
     * Gets the content type for a cell at the given 2D position.
     *
     * Content types are used by the lazy layout system to determine which cells can share the same composition slot,
     * enabling better performance through composition reuse.
     *
     * @param position The 2D position (column, row) of the cell.
     * @return The content type of the cell, or null if not specified.
     */
    public fun getContentType(position: IntOffset): Any?

    /**
     * Gets the content type for a cell at the given linear index.
     *
     * This is a convenience method that converts the linear index to a 2D position and retrieves the content type.
     *
     * @param index The linear index of the cell.
     * @return The content type of the cell, or null if not specified.
     * @see getContentType
     */
    public fun getContentType(index: Int): Any?

    /**
     * Converts a linear index to a 2D position (column, row).
     *
     * The linear index represents cells in row-major order, where cells are numbered sequentially from left to right,
     * top to bottom.
     *
     * @param index The linear index of the cell.
     * @return The 2D position as an [IntOffset] where x is the column and y is the row.
     */
    public fun getPosition(index: Int): IntOffset

    /**
     * Converts a 2D position (column, row) to a linear index.
     *
     * This is the inverse operation of [getPosition], converting a 2D coordinate back to its sequential index in
     * row-major order.
     *
     * @param position The 2D position of the cell where x is the column and y is the row.
     * @return The linear index of the cell.
     */
    public fun getIndex(position: IntOffset): Int

    /**
     * Gets the size constraints for a specific column.
     *
     * Size constraints define how the column should be measured and laid out, including minimum, maximum, and preferred
     * dimensions.
     *
     * @param column The column index.
     * @return The [LazyTableScope.ColumnSize] constraints for the column, or null if not specified.
     * @receiver The [LazyTableLayoutScope] providing access to layout context.
     */
    public fun LazyTableLayoutScope.getColumnConstraints(column: Int): LazyTableScope.ColumnSize?

    /**
     * Gets the size constraints for a specific row.
     *
     * Size constraints define how the row should be measured and laid out, including minimum, maximum, and preferred
     * dimensions.
     *
     * @param row The row index.
     * @return The [LazyTableScope.RowSize] constraints for the row, or null if not specified.
     * @receiver The [LazyTableLayoutScope] providing access to layout context.
     */
    public fun LazyTableLayoutScope.getRowConstraints(row: Int): LazyTableScope.RowSize?

    /**
     * Composes the cell content for the item at the given linear index.
     *
     * This method is called by the lazy layout system to render visible cells. It should compose the UI for the cell at
     * the specified index using the provided scope.
     *
     * @param scope The [LazyTableItemScope] providing layout information and modifiers for the cell.
     * @param index The linear index of the cell to compose.
     */
    @Composable public fun Item(scope: LazyTableItemScope, index: Int)
}
