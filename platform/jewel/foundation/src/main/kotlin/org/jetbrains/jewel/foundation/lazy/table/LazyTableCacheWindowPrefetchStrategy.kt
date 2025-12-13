// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * A [LazyTablePrefetchStrategy] implementation that uses [LazyLayoutCacheWindow] to manage item caching.
 *
 * This strategy maintains a cache window of items beyond the visible viewport, keeping them in memory to improve scroll
 * performance. It implements cache window logic similar to LazyGrid's CacheWindowLogic but adapted for tables.
 *
 * @param cacheWindow the [LazyLayoutCacheWindow] defining ahead and behind window sizes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
internal class LazyTableCacheWindowPrefetchStrategy(private val cacheWindow: LazyLayoutCacheWindow) :
    LazyTablePrefetchStrategy {
    // Cache window boundaries for rows
    internal var rowPrefetchWindowStartLine = Int.MAX_VALUE
        private set

    internal var rowPrefetchWindowEndLine = Int.MIN_VALUE
        private set

    // Cache window boundaries for columns
    internal var columnPrefetchWindowStartLine = Int.MAX_VALUE
        private set

    internal var columnPrefetchWindowEndLine = Int.MIN_VALUE
        private set

    // Cache for row/column sizes
    private val rowSizeCache = mutableMapOf<Int, Int>()
    private val columnSizeCache = mutableMapOf<Int, Int>()

    // Track previous scroll state
    private var previousPassDelta = Offset.Zero
    private var hasUpdatedVisibleItemsOnce = false

    // Track if we should refill the window
    private var shouldRefillWindow = false

    override fun LazyTablePrefetchScope.onScroll(delta: Offset, layoutInfo: LazyTableLayoutInfo) {
        if (layoutInfo.floatingItemsInfo.isEmpty()) return

        // Fill cache window in both directions
        fillCacheWindowForRows(delta.y, layoutInfo)
        fillCacheWindowForColumns(delta.x, layoutInfo)

        previousPassDelta = delta
    }

    override fun LazyTablePrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyTableLayoutInfo) {
        if (!hasUpdatedVisibleItemsOnce) {
            val density = layoutInfo.density
            val verticalViewportSize = layoutInfo.viewportEndOffset.y - layoutInfo.viewportStartOffset.y
            val horizontalViewportSize = layoutInfo.viewportEndOffset.x - layoutInfo.viewportStartOffset.x

            val prefetchForwardWindowVertical =
                with(cacheWindow) { with(density) { calculateAheadWindow(verticalViewportSize) } }
            val prefetchForwardWindowHorizontal =
                with(cacheWindow) { with(density) { calculateAheadWindow(horizontalViewportSize) } }

            // If we have a prefetch window, trigger initial fill
            if (prefetchForwardWindowVertical != 0 || prefetchForwardWindowHorizontal != 0) {
                shouldRefillWindow = true
            }
            hasUpdatedVisibleItemsOnce = true
        }

        if (layoutInfo.floatingItemsInfo.isNotEmpty()) {
            // Cache visible item sizes
            cacheVisibleItemSizes(layoutInfo)

            if (shouldRefillWindow) {
                // Initial window fill or refill after item count changes
                refillWindow(layoutInfo)
                shouldRefillWindow = false
            }
        }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(
        firstVisibleCellCoordinate: IntOffset,
        layoutInfo: LazyTableLayoutInfo,
    ) {
        // Prefetch a small grid of cells around the first visible cell
        val cellsToPreload = 4
        repeat(cellsToPreload) { i ->
            schedulePrecomposition(firstVisibleCellCoordinate.y * layoutInfo.columns + firstVisibleCellCoordinate.x + i)
        }
    }

    /** Checks if the cache window has valid bounds. */
    internal fun hasValidBounds(): Boolean =
        rowPrefetchWindowStartLine != Int.MAX_VALUE &&
            rowPrefetchWindowEndLine != Int.MIN_VALUE &&
            columnPrefetchWindowStartLine != Int.MAX_VALUE &&
            columnPrefetchWindowEndLine != Int.MIN_VALUE

    /** Gets the cache window boundaries for rows and columns. */
    internal fun getCacheWindowBoundaries(): CacheWindowBoundaries {
        return CacheWindowBoundaries(
            rowStart = rowPrefetchWindowStartLine,
            rowEnd = rowPrefetchWindowEndLine,
            columnStart = columnPrefetchWindowStartLine,
            columnEnd = columnPrefetchWindowEndLine,
        )
    }

    /**
     * Resets the prefetch strategy to its initial state. Clears all cache windows, size caches, and scroll tracking
     * state.
     */
    internal fun resetStrategy() {
        rowPrefetchWindowStartLine = Int.MAX_VALUE
        rowPrefetchWindowEndLine = Int.MIN_VALUE
        columnPrefetchWindowStartLine = Int.MAX_VALUE
        columnPrefetchWindowEndLine = Int.MIN_VALUE

        rowSizeCache.clear()
        columnSizeCache.clear()

        previousPassDelta = Offset.Zero
        hasUpdatedVisibleItemsOnce = false
        shouldRefillWindow = false
    }

    private fun cacheVisibleItemSizes(layoutInfo: LazyTableLayoutInfo) {
        layoutInfo.floatingItemsInfo.forEach { item ->
            rowSizeCache[item.row] = item.size.height
            columnSizeCache[item.column] = item.size.width
        }
    }

    private fun fillCacheWindowForRows(delta: Float, layoutInfo: LazyTableLayoutInfo) {
        if (layoutInfo.floatingItemsInfo.isEmpty()) return

        val density = layoutInfo.density
        val viewportSize = layoutInfo.viewportEndOffset.y - layoutInfo.viewportStartOffset.y

        val aheadWindow = with(cacheWindow) { with(density) { calculateAheadWindow(viewportSize) } }
        val behindWindow = with(cacheWindow) { with(density) { calculateBehindWindow(viewportSize) } }

        val firstVisibleRow = layoutInfo.floatingItemsInfo.first().row
        val lastVisibleRow = layoutInfo.floatingItemsInfo.last().row

        // Calculate window boundaries based on scroll direction
        if (delta < 0) {
            // Scrolling down (forward) - prefetch below
            rowPrefetchWindowStartLine = (firstVisibleRow - estimateRowCount(behindWindow)).coerceAtLeast(0)
            rowPrefetchWindowEndLine =
                (lastVisibleRow + estimateRowCount(aheadWindow)).coerceAtMost(layoutInfo.rows - 1)
        } else {
            // Scrolling up (backward) - prefetch above
            rowPrefetchWindowStartLine = (firstVisibleRow - estimateRowCount(aheadWindow)).coerceAtLeast(0)
            rowPrefetchWindowEndLine =
                (lastVisibleRow + estimateRowCount(behindWindow)).coerceAtMost(layoutInfo.rows - 1)
        }
    }

    private fun fillCacheWindowForColumns(delta: Float, layoutInfo: LazyTableLayoutInfo) {
        if (layoutInfo.floatingItemsInfo.isEmpty()) return

        val density = layoutInfo.density
        val viewportSize = layoutInfo.viewportEndOffset.x - layoutInfo.viewportStartOffset.x

        val aheadWindow = with(cacheWindow) { with(density) { calculateAheadWindow(viewportSize) } }
        val behindWindow = with(cacheWindow) { with(density) { calculateBehindWindow(viewportSize) } }

        val firstVisibleColumn = layoutInfo.floatingItemsInfo.first().column
        val lastVisibleColumn = layoutInfo.floatingItemsInfo.last().column

        // Calculate window boundaries based on scroll direction
        if (delta < 0) {
            // Scrolling right (forward) - prefetch to the right
            columnPrefetchWindowStartLine = (firstVisibleColumn - estimateColumnCount(behindWindow)).coerceAtLeast(0)
            columnPrefetchWindowEndLine =
                (lastVisibleColumn + estimateColumnCount(aheadWindow)).coerceAtMost(layoutInfo.columns - 1)
        } else {
            // Scrolling left (backward) - prefetch to the left
            columnPrefetchWindowStartLine = (firstVisibleColumn - estimateColumnCount(aheadWindow)).coerceAtLeast(0)
            columnPrefetchWindowEndLine =
                (lastVisibleColumn + estimateColumnCount(behindWindow)).coerceAtMost(layoutInfo.columns - 1)
        }
    }

    private fun refillWindow(layoutInfo: LazyTableLayoutInfo) {
        // Use previous delta to determine scroll direction for initial fill
        fillCacheWindowForRows(previousPassDelta.y, layoutInfo)
        fillCacheWindowForColumns(previousPassDelta.x, layoutInfo)
    }

    private fun estimateRowCount(windowSizePixels: Int): Int {
        if (windowSizePixels <= 0 || rowSizeCache.isEmpty()) return 0

        // Use average row height from cache
        val averageRowHeight = rowSizeCache.values.average()
        return if (averageRowHeight <= 0.0) {
            0
        } else {
            return (windowSizePixels / averageRowHeight).roundToInt()
        }
    }

    private fun estimateColumnCount(windowSizePixels: Int): Int {
        if (windowSizePixels <= 0 || columnSizeCache.isEmpty()) return 0

        // Use average column width from cache
        val averageColumnWidth = columnSizeCache.values.average()
        return if (averageColumnWidth <= 0.0) {
            0
        } else {
            (windowSizePixels / averageColumnWidth).roundToInt()
        }
    }
}

/** Data class holding cache window boundaries for both rows and columns. */
internal data class CacheWindowBoundaries(val rowStart: Int, val rowEnd: Int, val columnStart: Int, val columnEnd: Int)
