// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset

/**
 * Implementations of this interface control which cells of a LazyTable should be prefetched (precomposed and
 * premeasured during idle time) as the user interacts with it.
 *
 * Implementations should invoke [LazyTablePrefetchScope.scheduleRowPrefetch] and
 * [LazyTablePrefetchScope.scheduleColumnPrefetch] to schedule prefetches from the [onScroll] and
 * [onVisibleItemsUpdated] callbacks. If any of the returned PrefetchHandles no longer need to be prefetched, use
 * [LazyLayoutPrefetchState.PrefetchHandle.cancel] to cancel the request.
 */
@Stable
public interface LazyTablePrefetchStrategy {
    /**
     * onScroll is invoked when the LazyTable scrolls, whether or not the visible items have changed. If the visible
     * items have also changed, then this will be invoked in the same frame *after* [onVisibleItemsUpdated].
     *
     * @param delta the change in scroll direction. Delta.x < 0 indicates scrolling right while delta.x > 0 indicates
     *   scrolling left. Delta.y < 0 indicates scrolling down while delta.y > 0 indicates scrolling up.
     * @param layoutInfo the current [LazyTableLayoutInfo]
     */
    public fun LazyTablePrefetchScope.onScroll(delta: Offset, layoutInfo: LazyTableLayoutInfo)

    /**
     * onVisibleItemsUpdated is invoked when the LazyTable scrolls if the visible items have changed.
     *
     * @param layoutInfo the current [LazyTableLayoutInfo]. Info about the updated visible items can be found in
     *   [LazyTableLayoutInfo.floatingItemsInfo].
     */
    public fun LazyTablePrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyTableLayoutInfo)

    /**
     * onNestedPrefetch is invoked when a parent LazyLayout has prefetched content which contains this LazyTable. It
     * gives this LazyTable a chance to request prefetch for some of its own children before coming onto screen.
     *
     * Implementations can use [NestedPrefetchScope.schedulePrefetch] to schedule child prefetches. For example, this is
     * useful if this LazyTable is a child of a LazyColumn: in that case, [onNestedPrefetch] can schedule the cells it
     * expects to be visible when it comes onto screen, giving the LazyLayout infra a chance to compose these children
     * ahead of time and reduce jank.
     *
     * Generally speaking, [onNestedPrefetch] should only request prefetch for children that it expects to actually be
     * visible when this table is scrolled into view.
     *
     * @param firstVisibleCellCoordinate the coordinate of the first visible cell. It should be used to start
     *   prefetching from the correct position in case the table has been created at a non-zero offset.
     */
    public fun NestedPrefetchScope.onNestedPrefetch(
        firstVisibleCellCoordinate: IntOffset,
        layoutInfo: LazyTableLayoutInfo,
    )
}

/** Scope for callbacks in [LazyTablePrefetchStrategy] which allows prefetches to be requested. */
@Stable
public interface LazyTablePrefetchScope {
    /**
     * Schedules a prefetch for the given row index. All columns in this row will be prefetched. Requests are executed
     * in the order they're requested. If a requested prefetch is no longer necessary (for example, due to changing
     * scroll direction), the request should be canceled via [LazyLayoutPrefetchState.PrefetchHandle.cancel].
     *
     * @param rowIndex index of the row to prefetch
     * @return list of prefetch handles for all cells in the row
     */
    public fun scheduleRowPrefetch(rowIndex: Int): List<LazyLayoutPrefetchState.PrefetchHandle>

    /**
     * Schedules a prefetch for the given column index. All rows in this column will be prefetched. Requests are
     * executed in the order they're requested. If a requested prefetch is no longer necessary (for example, due to
     * changing scroll direction), the request should be canceled via [LazyLayoutPrefetchState.PrefetchHandle.cancel].
     *
     * @param columnIndex index of the column to prefetch
     * @return list of prefetch handles for all cells in the column
     */
    public fun scheduleColumnPrefetch(columnIndex: Int): List<LazyLayoutPrefetchState.PrefetchHandle>

    /**
     * Schedules a prefetch for a specific cell at the given coordinate.
     *
     * @param column the column index of the cell
     * @param row the row index of the cell
     * @return prefetch handle for the cell
     */
    public fun scheduleCellPrefetch(column: Int, row: Int): LazyLayoutPrefetchState.PrefetchHandle
}

/**
 * Creates an instance of the default [LazyTablePrefetchStrategy], allowing for customization of the nested prefetch
 * count.
 *
 * @param nestedPrefetchItemCount specifies how many inner items should be prefetched when this LazyTable is nested
 *   inside another LazyLayout. For example, if this is the state for a LazyTable nested in a vertical LazyColumn, you
 *   might want to set this to the number of cells that will be visible when this table is scrolled into view. If
 *   automatic nested prefetch is enabled, this value will be used as the initial count and the strategy will adapt the
 *   count automatically.
 */
public fun LazyTablePrefetchStrategy(nestedPrefetchItemCount: Int = 4): LazyTablePrefetchStrategy =
    DefaultLazyTablePrefetchStrategy(nestedPrefetchItemCount)

/**
 * The default prefetching strategy for LazyTables - this will be used automatically if no other strategy is provided.
 */
@Stable
private class DefaultLazyTablePrefetchStrategy(private val initialNestedPrefetchItemCount: Int = 4) :
    LazyTablePrefetchStrategy {
    /** The row/column scheduled to be prefetched (or the last prefetched row/column if the prefetch is done). */
    private var rowToPrefetch = -1
    private var columnToPrefetch = -1

    /** The list of handles associated with the current prefetch. */
    private val currentRowPrefetchHandles = mutableVectorOf<LazyLayoutPrefetchState.PrefetchHandle>()
    private val currentColumnPrefetchHandles = mutableVectorOf<LazyLayoutPrefetchState.PrefetchHandle>()

    /**
     * Keeps the scrolling direction during the previous calculation in order to be able to detect the scrolling
     * direction change.
     */
    private var wasScrollingHorizontallyForward = false
    private var wasScrollingVerticallyForward = false

    private var previousPassItemCount = UNSET_ITEM_COUNT
    private var previousPassDelta = Offset.Zero

    @Suppress("NestedBlockDepth")
    override fun LazyTablePrefetchScope.onScroll(delta: Offset, layoutInfo: LazyTableLayoutInfo) {
        if (layoutInfo.floatingItemsInfo.isNotEmpty()) {
            val scrollingHorizontallyForward = delta.x < 0
            val scrollingVerticallyForward = delta.y < 0

            // Handle horizontal prefetch
            if (delta.x != 0f) {
                val columnToPrefetch = layoutInfo.calculateColumnToPrefetch(scrollingHorizontallyForward)
                val closestNextColumnToPrefetch =
                    layoutInfo.calculateClosestNextColumnToPrefetch(scrollingHorizontallyForward)

                if (
                    closestNextColumnToPrefetch in 0 until layoutInfo.columns &&
                        columnToPrefetch != this@DefaultLazyTablePrefetchStrategy.columnToPrefetch &&
                        columnToPrefetch >= 0
                ) {
                    if (wasScrollingHorizontallyForward != scrollingHorizontallyForward) {
                        // Scrolling direction changed - cancel current prefetch
                        currentColumnPrefetchHandles.forEach { it.cancel() }
                    }
                    this@DefaultLazyTablePrefetchStrategy.wasScrollingHorizontallyForward = scrollingHorizontallyForward
                    this@DefaultLazyTablePrefetchStrategy.columnToPrefetch = columnToPrefetch
                    currentColumnPrefetchHandles.clear()
                    currentColumnPrefetchHandles.addAll(scheduleColumnPrefetch(columnToPrefetch))
                }

                // Mark as urgent if we're close to reaching the prefetched column
                if (scrollingHorizontallyForward && layoutInfo.floatingItemsInfo.isNotEmpty()) {
                    val lastItem = layoutInfo.floatingItemsInfo.last()
                    val distanceToPrefetchColumn =
                        lastItem.offset.x + lastItem.size.width + layoutInfo.horizontalSpacing -
                            layoutInfo.viewportEndOffset.x
                    if (distanceToPrefetchColumn < -delta.x) {
                        currentColumnPrefetchHandles.forEach { it.markAsUrgent() }
                    }
                } else if (!scrollingHorizontallyForward && layoutInfo.floatingItemsInfo.isNotEmpty()) {
                    val firstItem = layoutInfo.floatingItemsInfo.first()
                    val distanceToPrefetchColumn =
                        layoutInfo.viewportStartOffset.x + layoutInfo.pinnedColumnsWidth - firstItem.offset.x
                    if (distanceToPrefetchColumn < delta.x) {
                        currentColumnPrefetchHandles.forEach { it.markAsUrgent() }
                    }
                }
            }

            // Handle vertical prefetch
            if (delta.y != 0f) {
                val rowToPrefetch = layoutInfo.calculateRowToPrefetch(scrollingVerticallyForward)
                val closestNextRowToPrefetch = layoutInfo.calculateClosestNextRowToPrefetch(scrollingVerticallyForward)

                if (
                    closestNextRowToPrefetch in 0 until layoutInfo.rows &&
                        rowToPrefetch != this@DefaultLazyTablePrefetchStrategy.rowToPrefetch &&
                        rowToPrefetch >= 0
                ) {
                    if (wasScrollingVerticallyForward != scrollingVerticallyForward) {
                        // Scrolling direction changed - cancel current prefetch
                        currentRowPrefetchHandles.forEach { it.cancel() }
                    }
                    this@DefaultLazyTablePrefetchStrategy.wasScrollingVerticallyForward = scrollingVerticallyForward
                    this@DefaultLazyTablePrefetchStrategy.rowToPrefetch = rowToPrefetch
                    currentRowPrefetchHandles.clear()
                    currentRowPrefetchHandles.addAll(scheduleRowPrefetch(rowToPrefetch))
                }

                // Mark as urgent if we're close to reaching the prefetched row
                if (scrollingVerticallyForward && layoutInfo.floatingItemsInfo.isNotEmpty()) {
                    val lastItem = layoutInfo.floatingItemsInfo.last()
                    val distanceToPrefetchRow =
                        lastItem.offset.y + lastItem.size.height + layoutInfo.verticalSpacing -
                            layoutInfo.viewportEndOffset.y
                    if (distanceToPrefetchRow < -delta.y) {
                        currentRowPrefetchHandles.forEach { it.markAsUrgent() }
                    }
                } else if (!scrollingVerticallyForward && layoutInfo.floatingItemsInfo.isNotEmpty()) {
                    val firstItem = layoutInfo.floatingItemsInfo.first()
                    val distanceToPrefetchRow =
                        layoutInfo.viewportStartOffset.y + layoutInfo.pinnedRowsHeight - firstItem.offset.y
                    if (distanceToPrefetchRow < delta.y) {
                        currentRowPrefetchHandles.forEach { it.markAsUrgent() }
                    }
                }
            }
        }
        previousPassDelta = delta
    }

    override fun LazyTablePrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyTableLayoutInfo) {
        layoutInfo.evaluatePrefetchForCancellation(
            currentPrefetchingRow = rowToPrefetch,
            currentPrefetchingColumn = columnToPrefetch,
            scrollingVerticallyForward = wasScrollingVerticallyForward,
            scrollingHorizontallyForward = wasScrollingHorizontallyForward,
        )

        val currentPassItemCount = layoutInfo.totalItemsCount
        // Total item count changed, re-trigger prefetch
        @Suppress("ComplexCondition")
        if (
            previousPassItemCount != UNSET_ITEM_COUNT && // we already have info about the item count
                previousPassDelta != Offset.Zero && // and scroll direction
                previousPassItemCount != currentPassItemCount && // and the item count changed
                layoutInfo.floatingItemsInfo.isNotEmpty()
        ) {
            // Handle vertical prefetch re-trigger
            if (previousPassDelta.y != 0f) {
                val rowToPrefetch = layoutInfo.calculateRowToPrefetch(previousPassDelta.y < 0)
                val closestNextRowToPrefetch = layoutInfo.calculateClosestNextRowToPrefetch(previousPassDelta.y < 0)
                if (
                    closestNextRowToPrefetch in 0 until layoutInfo.rows &&
                        rowToPrefetch != this@DefaultLazyTablePrefetchStrategy.rowToPrefetch &&
                        rowToPrefetch >= 0
                ) {
                    this@DefaultLazyTablePrefetchStrategy.rowToPrefetch = rowToPrefetch
                    currentRowPrefetchHandles.clear()
                    currentRowPrefetchHandles.addAll(scheduleRowPrefetch(rowToPrefetch))
                }
            }

            // Handle horizontal prefetch re-trigger
            if (previousPassDelta.x != 0f) {
                val columnToPrefetch = layoutInfo.calculateColumnToPrefetch(previousPassDelta.x < 0)
                val closestNextColumnToPrefetch =
                    layoutInfo.calculateClosestNextColumnToPrefetch(previousPassDelta.x < 0)
                if (
                    closestNextColumnToPrefetch in 0 until layoutInfo.columns &&
                        columnToPrefetch != this@DefaultLazyTablePrefetchStrategy.columnToPrefetch &&
                        columnToPrefetch >= 0
                ) {
                    this@DefaultLazyTablePrefetchStrategy.columnToPrefetch = columnToPrefetch
                    currentColumnPrefetchHandles.clear()
                    currentColumnPrefetchHandles.addAll(scheduleColumnPrefetch(columnToPrefetch))
                }
            }
        }

        previousPassItemCount = currentPassItemCount
    }

    override fun NestedPrefetchScope.onNestedPrefetch(
        firstVisibleCellCoordinate: IntOffset,
        layoutInfo: LazyTableLayoutInfo,
    ) {
        val resolvedNestedPrefetchItemCount =
            if (nestedPrefetchItemCount == UNSPECIFIED_NESTED_PREFETCH) {
                initialNestedPrefetchItemCount
            } else {
                nestedPrefetchItemCount
            }
        // Prefetch a small grid of cells around the first visible cell
        // For a table, we prefetch both horizontal and vertical neighbors
        val cellsToPreload = minOf(resolvedNestedPrefetchItemCount, 4) // Limit to reasonable number
        repeat(cellsToPreload) { i ->
            schedulePrecomposition(layoutInfo.calculateIndexFromCoordinate(firstVisibleCellCoordinate, i))
        }
    }

    private fun LazyTableLayoutInfo.calculateIndexFromCoordinate(coordinate: IntOffset, offset: Int): Int =
        // Simple linear index calculation - can be customized based on table structure
        coordinate.y * columns + coordinate.x + offset

    private fun LazyTableLayoutInfo.evaluatePrefetchForCancellation(
        currentPrefetchingRow: Int,
        currentPrefetchingColumn: Int,
        scrollingVerticallyForward: Boolean,
        scrollingHorizontallyForward: Boolean,
    ) {
        if (floatingItemsInfo.isNotEmpty()) {
            // Evaluate row prefetch cancellation
            if (currentPrefetchingRow != -1) {
                val expectedRowToPrefetch = calculateRowToPrefetch(scrollingVerticallyForward)
                if (currentPrefetchingRow != expectedRowToPrefetch) {
                    resetRowPrefetchState()
                }
            }

            // Evaluate column prefetch cancellation
            if (currentPrefetchingColumn != -1) {
                val expectedColumnToPrefetch = calculateColumnToPrefetch(scrollingHorizontallyForward)
                if (currentPrefetchingColumn != expectedColumnToPrefetch) {
                    resetColumnPrefetchState()
                }
            }
        }
    }

    private fun LazyTableLayoutInfo.calculateRowToPrefetch(scrollingForward: Boolean): Int {
        return if (scrollingForward) {
            floatingItemsInfo.lastOrNull()?.row?.plus(1) ?: -1
        } else {
            floatingItemsInfo.firstOrNull()?.row?.minus(1) ?: -1
        }
    }

    private fun LazyTableLayoutInfo.calculateColumnToPrefetch(scrollingForward: Boolean): Int {
        return if (scrollingForward) {
            floatingItemsInfo.lastOrNull()?.column?.plus(1) ?: -1
        } else {
            floatingItemsInfo.firstOrNull()?.column?.minus(1) ?: -1
        }
    }

    private fun LazyTableLayoutInfo.calculateClosestNextRowToPrefetch(scrollingForward: Boolean): Int {
        return if (scrollingForward) {
            floatingItemsInfo.lastOrNull()?.row?.plus(1) ?: -1
        } else {
            floatingItemsInfo.firstOrNull()?.row?.minus(1) ?: -1
        }
    }

    private fun LazyTableLayoutInfo.calculateClosestNextColumnToPrefetch(scrollingForward: Boolean): Int {
        return if (scrollingForward) {
            floatingItemsInfo.lastOrNull()?.column?.plus(1) ?: -1
        } else {
            floatingItemsInfo.firstOrNull()?.column?.minus(1) ?: -1
        }
    }

    private fun resetRowPrefetchState() {
        rowToPrefetch = -1
        currentRowPrefetchHandles.forEach { it.cancel() }
        currentRowPrefetchHandles.clear()
    }

    private fun resetColumnPrefetchState() {
        columnToPrefetch = -1
        currentColumnPrefetchHandles.forEach { it.cancel() }
        currentColumnPrefetchHandles.clear()
    }
}

private const val UNSET_ITEM_COUNT = -1
private const val UNSPECIFIED_NESTED_PREFETCH = -1
