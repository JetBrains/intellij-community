// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions

public interface LazyTableLayoutInfo {
    public val floatingItemsInfo: List<LazyTableItemInfo>

    public val pinnedColumnsInfo: List<LazyTableItemInfo>

    public val pinnedRowsInfo: List<LazyTableItemInfo>

    public val pinnedItemsInfo: List<LazyTableItemInfo>

    public val viewportStartOffset: IntOffset

    public val viewportEndOffset: IntOffset

    public val density: Density

    public val viewportSize: IntSize
        get() = IntSize.Zero

    public val viewportCellSize: IntSize
        get() = IntSize.Zero

    public val columns: Int

    public val rows: Int

    public val pinnedColumns: Int

    public val pinnedRows: Int

    public val pinnedColumnsWidth: Int

    public val pinnedRowsHeight: Int

    public val totalItemsCount: Int
        get() = columns * rows

    public val horizontalSpacing: Int
        get() = 0

    public val verticalSpacing: Int
        get() = 0
}

@GenerateDataFunctions
public class LazyTableInfo(
    public val columns: Int = 0,
    public val rows: Int = 0,
    public val pinnedColumns: Int = 0,
    public val pinnedRows: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LazyTableInfo

        if (columns != other.columns) return false
        if (rows != other.rows) return false
        if (pinnedColumns != other.pinnedColumns) return false
        if (pinnedRows != other.pinnedRows) return false

        return true
    }

    override fun hashCode(): Int {
        var result = columns
        result = 31 * result + rows
        result = 31 * result + pinnedColumns
        result = 31 * result + pinnedRows
        return result
    }

    override fun toString(): String =
        "LazyTableInfo(columns=$columns, rows=$rows, pinnedColumns=$pinnedColumns, pinnedRows=$pinnedRows)"

    public companion object {
        public val Empty: LazyTableInfo = LazyTableInfo()
    }
}
