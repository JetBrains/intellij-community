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
    private val cachedColumnConstraints = HashMap<Int, LazyTableScope.ColumnSize>(100)
    private val cachedRowConstraints = HashMap<Int, LazyTableScope.RowSize>(100)

    private val measuredItems = HashMap<IntOffset, LazyTableMeasuredItem>(500)

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
            Constraints(
                minWidth =
                    columnConstraints
                        .minWidth(availableSize, this@LazyTableMeasuredItemProvider)
                        .coerceAtLeast(15.dp.roundToPx()),
                maxWidth =
                    columnConstraints
                        .maxWidth(availableSize, this@LazyTableMeasuredItemProvider)
                        .coerceAtLeast(15.dp.roundToPx()),
                minHeight =
                    rowConstraints
                        .minHeight(availableSize, this@LazyTableMeasuredItemProvider)
                        .coerceAtLeast(textStyle.lineHeight.roundToPx()),
                maxHeight =
                    rowConstraints
                        .maxHeight(availableSize, this@LazyTableMeasuredItemProvider)
                        .coerceAtLeast(textStyle.lineHeight.roundToPx()),
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
        val placeables = measureScope.compose(index).map { it.measure(constraints) }

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
        if (column >= columns - 1 || row >= rows - 1) {
            return null
        }

        return getAndMeasure(column, row)
    }

    fun getRowHeight(row: Int): Int = getAndMeasure(0, row).size.height

    fun getColumnWidth(column: Int): Int = getAndMeasure(column, 0).size.width

    abstract fun createItem(
        column: Int,
        row: Int,
        size: IntSize,
        key: Any,
        contentType: Any?,
        placeables: List<Placeable>,
    ): LazyTableMeasuredItem
}
