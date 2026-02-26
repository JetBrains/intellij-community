// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.util.fastMap
import kotlin.math.max

internal abstract class LazyTableMeasuredItemProvider(
    override val availableSize: IntSize,
    override val columns: Int,
    override val rows: Int,
    override val horizontalSpacing: Int,
    override val verticalSpacing: Int,
    private val itemProvider: LazyTableItemProvider,
    private val measureScope: LazyLayoutMeasureScope,
    private val textStyle: TextStyle,
    density: Density,
) : LazyTableLayoutScope, Density by density {
    private val cachedColumnConstraints =
        LRUCache<Int, LazyTableScope.ColumnSize>(columns.coerceAtMost(MAX_CACHED_CONSTRAINTS))
    private val cachedRowConstraints = LRUCache<Int, LazyTableScope.RowSize>(rows.coerceAtMost(MAX_CACHED_CONSTRAINTS))

    private val cachedColumnWidths = LRUCache<Int, Int>(columns.coerceAtMost(MAX_CACHED_CONSTRAINTS))
    private val cachedRowHeights = LRUCache<Int, Int>(rows.coerceAtMost(MAX_CACHED_CONSTRAINTS))

    private val measuredItems = LRUCache<IntOffset, LazyTableMeasuredItem>(MAX_CACHED_MEASURED_ITEMS)

    private fun getCellConstraints(column: Int, row: Int): Constraints =
        with(itemProvider) {
            val rowConstraints =
                cachedRowConstraints.getOrPut(row) { getRowConstraints(row) ?: LazyTableScope.RowSize.Constrained() }
            val columnConstraints =
                cachedColumnConstraints.getOrPut(column) {
                    getColumnConstraints(column) ?: LazyTableScope.ColumnSize.Constrained()
                }

            // The default min width for tables is 15.dp
            // The default min height is the same from the TextStyle line height
            // Setting a default to 15.sp if the lineHeight is unspecified
            val minWidth = 15.dp.roundToPx()
            val minHeight = textStyle.lineHeight.takeOrElse { 15.sp }.roundToPx()

            Constraints(
                minWidth =
                    columnConstraints
                        .minWidth(size = availableSize, density = this@LazyTableMeasuredItemProvider)
                        .coerceAtLeast(minWidth),
                maxWidth =
                    columnConstraints
                        .maxWidth(size = availableSize, density = this@LazyTableMeasuredItemProvider)
                        .coerceAtLeast(minWidth),
                minHeight =
                    rowConstraints
                        .minHeight(size = availableSize, density = this@LazyTableMeasuredItemProvider)
                        .coerceAtLeast(minHeight),
                maxHeight =
                    rowConstraints
                        .maxHeight(size = availableSize, density = this@LazyTableMeasuredItemProvider)
                        .coerceAtLeast(minHeight),
            )
        }

    private fun getOrMeasureColumnHeader(column: Int): Int =
        measuredItems
            .getOrPut(IntOffset(column, 0)) { getAndMeasure(column, 0, getCellConstraints(column, 0)) }
            .size
            .width

    private fun getOrMeasureRowHeader(row: Int): Int =
        measuredItems.getOrPut(IntOffset(0, row)) { getAndMeasure(0, row, getCellConstraints(0, row)) }.size.height

    private fun getAndMeasure(column: Int, row: Int, constraints: Constraints): LazyTableMeasuredItem {
        val coordinate = IntOffset(column, row)
        val index = itemProvider.getIndex(coordinate)
        val key = itemProvider.getKey(coordinate)
        val contentType = itemProvider.getContentType(coordinate)
        val placeables = measureScope.compose(index).fastMap { it.measure(constraints) }

        var maxWidth = 0
        var maxHeight = 0

        for (placeable in placeables) {
            maxWidth = max(placeable.width, maxWidth)
            maxHeight = max(placeable.height, maxHeight)
        }

        return createItem(coordinate.x, coordinate.y, IntSize(maxWidth, maxHeight), key, contentType, placeables)
    }

    fun getAndMeasure(column: Int, row: Int): LazyTableMeasuredItem {
        val width = getOrMeasureColumnHeader(column)
        val height = getOrMeasureRowHeader(row)

        return measuredItems.getOrPut(IntOffset(column, row)) {
            getAndMeasure(column, row, Constraints.fixed(width, height))
        }
    }

    fun getAndMeasureOrNull(column: Int, row: Int): LazyTableMeasuredItem? {
        if (column >= columns || row >= rows) {
            return null
        }

        return getAndMeasure(column, row)
    }

    fun getRowHeight(row: Int): Int = cachedRowHeights.getOrPut(row) { getAndMeasure(0, row).size.height }

    fun getColumnWidth(column: Int): Int = cachedColumnWidths.getOrPut(column) { getAndMeasure(column, 0).size.width }

    /**
     * Returns the constraints for the given cell position. Used by the prefetch system to pre-measure items with
     * correct constraints.
     */
    fun getConstraintsFor(column: Int, row: Int): Constraints = getCellConstraints(column, row)

    /**
     * Utility method to be used during the keep-around pass. This ensures items in the cache window are measured and
     * kept in memory to avoid recomposition when they become visible again.
     *
     * This is semantically equivalent to [getAndMeasure] but provides clarity about its use in the cache window logic.
     */
    fun keepAroundRow(rowIndex: Int, columnRange: IntRange) {
        // Keep around all cells in the row by measuring them
        for (column in columnRange) {
            getAndMeasure(column, rowIndex)
        }
    }

    /**
     * Utility method to be used during the keep-around pass for columns. This ensures items in the cache window are
     * measured and kept in memory to avoid recomposition when they become visible again.
     */
    fun keepAroundColumn(columnIndex: Int, rowRange: IntRange) {
        // Keep around all cells in the column by measuring them
        for (row in rowRange) {
            getAndMeasure(columnIndex, row)
        }
    }

    abstract fun createItem(
        column: Int,
        row: Int,
        size: IntSize,
        key: Any,
        contentType: Any?,
        placeables: List<Placeable>,
    ): LazyTableMeasuredItem
}

private class LRUCache<K : Any, V : Any>(private val capacity: Int) : LinkedHashMap<K, V>(capacity, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
}

private const val MAX_CACHED_CONSTRAINTS = 500
private const val MAX_CACHED_MEASURED_ITEMS = 2000
