// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.unit.IntOffset

internal interface LazyTableItemKeyPositionMap {
    fun getPosition(key: Any): IntOffset?

    fun getKey(coordinate: IntOffset): Any?

    companion object Empty : LazyTableItemKeyPositionMap {
        override fun getPosition(key: Any): IntOffset? = null

        override fun getKey(coordinate: IntOffset): Any? = null
    }
}

internal class NearestRangeKeyPositionMap(
    rowRange: IntRange,
    columnRange: IntRange,
    pinnedColumns: Int,
    pinnedRows: Int,
    content: LazyTableContent,
) : LazyTableItemKeyPositionMap {
    private val map: Map<Any, IntOffset>
    private val keys: Map<IntOffset, Any>

    init {
        val firstRow = rowRange.first
        val lastRow = minOf(rowRange.last, content.rowCount - 1)
        val rowCount = lastRow - firstRow + 1 + pinnedRows

        val firstColumn = columnRange.first
        val lastColumn = minOf(columnRange.last, content.columnCount - 1)
        val columnCount = lastColumn - firstColumn + 1 + pinnedColumns

        val pinnedRowCount = minOf(pinnedRows, content.rowCount)
        val pinnedColumnCount = minOf(pinnedColumns, content.columnCount)

        if (lastRow < firstRow || lastColumn < firstColumn) {
            map = emptyMap()
            keys = emptyMap()
        } else {
            keys = HashMap(rowCount * columnCount)
            map = HashMap(rowCount * columnCount)

            fun initCell(column: Int, row: Int) {
                val position = IntOffset(column, row)
                if (position in keys) return

                val key = content.getKey(position)
                map[key] = position
                keys[position] = key
            }

            repeat(pinnedRowCount) {
                for (column in firstColumn..lastColumn) {
                    initCell(column, it)
                }
            }

            repeat(pinnedColumnCount) {
                for (row in firstRow..lastRow) {
                    initCell(it, row)
                }
            }

            for (row in firstRow..lastRow) {
                for (column in firstColumn..lastColumn) {
                    initCell(column, row)
                }
            }
        }
    }

    override fun getPosition(key: Any): IntOffset? = map[key]

    override fun getKey(coordinate: IntOffset): Any? = keys[coordinate]
}
