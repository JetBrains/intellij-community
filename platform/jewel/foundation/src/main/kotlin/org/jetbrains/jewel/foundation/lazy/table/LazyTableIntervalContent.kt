// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.foundation.lazy.layout.getDefaultLazyLayoutKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset

/**
 * Internal implementation of [LazyTableScope] and [LazyTableContent] that manages the interval-based content structure
 * of a lazy table.
 *
 * This class stores column and row definitions as intervals, allowing efficient management of large tables where items
 * are defined in ranges rather than individually. It handles the mapping between linear indices and 2D positions, and
 * provides access to cell content, keys, and constraints.
 *
 * @param content The DSL builder that defines the table structure (columns and rows).
 * @param state The [LazyTableState] associated with this table.
 * @see LazyTableScope
 * @see LazyTableContent
 */
internal class LazyTableIntervalContent(content: LazyTableScope.() -> Unit, val state: LazyTableState) :
    LazyTableScope, LazyTableContent {
    private val columnIntervals: MutableIntervalList<LazyTableColumnInterval> = MutableIntervalList()
    private val rowIntervals: MutableIntervalList<LazyTableRowInterval> = MutableIntervalList()

    override val columnCount: Int
        get() = columnIntervals.size

    override val rowCount: Int
        get() = rowIntervals.size

    init {
        apply(content)
    }

    override fun column(key: Any?, contentType: Any?, size: LazyTableScope.ColumnSize?) {
        columns(
            count = 1,
            key = if (key != null) { _: Int -> key } else null,
            contentType = { contentType },
            size = if (size != null) { _ -> size } else null,
        )
    }

    override fun columns(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        size: ((index: Int) -> LazyTableScope.ColumnSize)?,
    ) {
        columnIntervals.addInterval(count, LazyTableColumnInterval(key = key, type = contentType, size = size))
    }

    override fun row(key: Any?, contentType: Any?, size: LazyTableScope.RowSize?, cells: LazyTableRowScope.() -> Unit) {
        rows(
            count = 1,
            key = if (key != null) { _ -> key } else null,
            contentType = { contentType },
            size = if (size != null) { _ -> size } else null,
            cells = { cells() },
        )
    }

    override fun rows(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        size: ((index: Int) -> LazyTableScope.RowSize)?,
        cells: LazyTableRowScope.(index: Int) -> Unit,
    ) {
        rowIntervals.addInterval(
            count,
            LazyTableRowInterval(
                size = size,
                key = key,
                type = contentType,
                cellBuilder = { index ->
                    MutableIntervalList<LazyTableCellInterval>().apply {
                        val scope =
                            object : LazyTableRowScope {
                                override fun cell(
                                    key: Any?,
                                    contentType: Any?,
                                    content: @Composable (LazyTableCellScope.() -> Unit),
                                ) {
                                    cells(
                                        count = 1,
                                        key = if (key != null) { _ -> key } else null,
                                        contentType = { _ -> contentType },
                                        content = { _ -> content() },
                                    )
                                }

                                override fun cells(
                                    count: Int,
                                    key: ((index: Int) -> Any)?,
                                    contentType: (index: Int) -> Any?,
                                    content: @Composable (LazyTableCellScope.(index: Int) -> Unit),
                                ) {
                                    addInterval(count, LazyTableCellInterval(key, contentType, content))
                                }
                            }

                        scope.cells(index)
                    }
                },
            ),
        )
    }

    /**
     * Gets the unique key for a cell at the given 2D position.
     *
     * @param position The 2D position (column, row) of the cell.
     * @return A pair of keys (columnKey, rowKey) for the cell.
     */
    override fun getKey(position: IntOffset): Pair<Any, Any> {
        val (rowKey, rowCells) =
            rowIntervals.withInterval(position.y) { localIntervalIndex, content ->
                val rowKey = content.value.key?.invoke(localIntervalIndex) ?: getDefaultLazyLayoutKey(position.y)
                rowKey to content.value.rowCells(localIntervalIndex)
            }

        val columnKey =
            rowCells.withInterval(position.x) { localCellIntervalIndex, cellContent ->
                cellContent.value.key?.invoke(localCellIntervalIndex)
            }
                ?: columnIntervals.withInterval(position.x) { localCellIntervalIndex, cellContent ->
                    cellContent.value.key?.invoke(localCellIntervalIndex)
                }
                ?: getDefaultLazyLayoutKey(position.x)

        return columnKey to rowKey
    }

    /**
     * Gets the unique key for a cell at the given linear index.
     *
     * @param index The linear index of the cell.
     * @return A pair of keys (columnKey, rowKey) for the cell.
     */
    override fun getKey(index: Int): Pair<Any, Any> = getKey(getPosition(index))

    /**
     * Gets the content type for a cell at the given 2D position.
     *
     * @param position The 2D position (column, row) of the cell.
     * @return The content type of the cell, or null if not specified.
     */
    override fun getContentType(position: IntOffset): Any? = withCellAt(position) { index -> type.invoke(index) }

    /**
     * Gets the content type for a cell at the given linear index.
     *
     * @param index The linear index of the cell.
     * @return The content type of the cell, or null if not specified.
     */
    override fun getContentType(index: Int): Any? =
        withCellAt(getPosition(index)) { cellIndex -> type.invoke(cellIndex) }

    /**
     * Converts a linear index to a 2D position (column, row).
     *
     * @param index The linear index of the cell.
     * @return The 2D position as an [IntOffset].
     */
    override fun getPosition(index: Int): IntOffset {
        if (columnIntervals.size == 0) return IntOffset.Zero
        val row = index / columnIntervals.size
        val column = index % columnIntervals.size
        return IntOffset(column, row)
    }

    /**
     * Converts a 2D position (column, row) to a linear index.
     *
     * @param position The 2D position of the cell.
     * @return The linear index of the cell.
     */
    override fun getIndex(position: IntOffset): Int = position.y * columnIntervals.size + position.x

    /**
     * Gets the size constraints for a specific column.
     *
     * @param column The column index.
     * @return The [LazyTableScope.ColumnSize] constraints for the column, or null if not specified.
     * @receiver The [LazyTableLayoutScope] providing access to layout context.
     */
    override fun LazyTableLayoutScope.getColumnConstraints(column: Int): LazyTableScope.ColumnSize? =
        columnIntervals.withInterval(column) { localIntervalIndex, content ->
            content.value.size?.invoke(localIntervalIndex)
        }

    /**
     * Gets the size constraints for a specific row.
     *
     * @param row The row index.
     * @return The [LazyTableScope.RowSize] constraints for the row, or null if not specified.
     * @receiver The [LazyTableLayoutScope] providing access to layout context.
     */
    override fun LazyTableLayoutScope.getRowConstraints(row: Int): LazyTableScope.RowSize? =
        rowIntervals.withInterval(row) { localIntervalIndex, content -> content.value.size?.invoke(localIntervalIndex) }

    /**
     * Composes the cell content for the item at the given linear index.
     *
     * @param scope The [LazyTableItemScope] providing layout information.
     * @param index The linear index of the cell to compose.
     */
    @Composable
    override fun Item(scope: LazyTableItemScope, index: Int) {
        val position = getPosition(index)

        rowIntervals.withInterval(position.y) { localRowIndex, rowContent ->
            rowContent.value.rowCells(localRowIndex).withInterval(position.x) { localCellIntervalIndex, cellContent ->
                cellContent.value.item(
                    delegateLazyTableCellScope(scope, state, position, getKey(position)),
                    localCellIntervalIndex,
                )
            }
        }
    }

    /**
     * Executes a block with the cell interval at the given 2D position.
     *
     * @param position The 2D position of the cell.
     * @param content The block to execute with the cell interval.
     * @return The result of executing the block.
     */
    private inline fun <T> withCellAt(position: IntOffset, content: LazyTableCellInterval.(index: Int) -> T): T =
        rowIntervals.withInterval(position.y) { localIntervalIndex, rowContent ->
            rowContent.value.rowCells(localIntervalIndex).withInterval(position.x) { localCellIntervalIndex, cellContent
                ->
                cellContent.value.content(localCellIntervalIndex)
            }
        }

    /**
     * Executes a block with an interval and its local index within that interval.
     *
     * @param globalIndex The global index to locate within the intervals.
     * @param block The block to execute with the local index and interval.
     * @return The result of executing the block.
     */
    private inline fun <T, R> IntervalList<T>.withInterval(
        globalIndex: Int,
        block: (localIntervalIndex: Int, content: IntervalList.Interval<T>) -> R,
    ): R {
        val interval = this[globalIndex]
        val localIntervalIndex = globalIndex - interval.startIndex
        return block(localIntervalIndex, interval)
    }
}

/**
 * Represents an interval of columns in the lazy table.
 *
 * @param key Optional function to provide unique keys for columns in this interval.
 * @param type Function to provide content types for columns in this interval.
 * @param size Optional function to provide size constraints for columns in this interval.
 * @see LazyLayoutIntervalContent.Interval
 */
internal class LazyTableColumnInterval(
    override val key: ((index: Int) -> Any)?,
    override val type: ((index: Int) -> Any?),
    val size: ((index: Int) -> LazyTableScope.ColumnSize)?,
) : LazyLayoutIntervalContent.Interval

/**
 * Represents an interval of rows in the lazy table.
 *
 * This class caches the cell intervals for each row to avoid recomputing them on each access.
 *
 * @param key Optional function to provide unique keys for rows in this interval.
 * @param type Function to provide content types for rows in this interval.
 * @param size Optional function to provide size constraints for rows in this interval.
 * @param cellBuilder Function that builds the cell intervals for a specific row.
 * @see LazyLayoutIntervalContent.Interval
 */
internal class LazyTableRowInterval(
    override val key: ((index: Int) -> Any)?,
    override val type: ((index: Int) -> Any?),
    val size: ((index: Int) -> LazyTableScope.RowSize)?,
    private val cellBuilder: (index: Int) -> IntervalList<LazyTableCellInterval>,
) : LazyLayoutIntervalContent.Interval {
    /** Cache of cell intervals for each row to avoid recomputing them. */
    private val rowCellsCache = mutableMapOf<Int, IntervalList<LazyTableCellInterval>>()

    /**
     * Returns the cell intervals for the row at the given index, using the cache if available.
     *
     * @param index The local index of the row within this interval.
     * @return The list of cell intervals for the row.
     */
    fun rowCells(index: Int): IntervalList<LazyTableCellInterval> = rowCellsCache.getOrPut(index) { cellBuilder(index) }
}

/**
 * Represents an interval of cells within a row.
 *
 * @param key Optional function to provide unique keys for cells in this interval.
 * @param type Function to provide content types for cells in this interval.
 * @param item The composable function that renders the cell content.
 * @see LazyLayoutIntervalContent.Interval
 */
internal class LazyTableCellInterval(
    override val key: ((index: Int) -> Any)?,
    override val type: ((index: Int) -> Any?),
    val item: @Composable LazyTableCellScope.(index: Int) -> Unit,
) : LazyLayoutIntervalContent.Interval

/**
 * Creates a [LazyTableCellScope] that delegates to the given [LazyTableItemScope] and provides additional cell-specific
 * information.
 *
 * @param lazyTableItemScope The item scope to delegate to.
 * @param tableState The table state.
 * @param position The 2D position of the cell.
 * @param key The unique key pair for the cell.
 * @return A [LazyTableCellScope] with the combined functionality.
 */
private fun delegateLazyTableCellScope(
    lazyTableItemScope: LazyTableItemScope,
    tableState: LazyTableState,
    position: IntOffset,
    key: Pair<Any?, Any?>,
) =
    object : LazyTableCellScope, LazyTableItemScope by lazyTableItemScope {
        override val tableState: LazyTableState
            get() = tableState

        override val position: IntOffset
            get() = position

        override val key: Pair<Any?, Any?>
            get() = key
    }
