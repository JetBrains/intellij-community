// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table.selectable

import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.lazy.selectable.SelectionEvent
import org.jetbrains.jewel.foundation.lazy.selectable.SelectionType

@GenerateDataFunctions
public class TableSelectionEvent(
    public val columnKey: Any? = null,
    public val rowKey: Any? = null,
    public val selectionUnit: TableSelectionUnit = TableSelectionUnit.Cell,
    public val type: SelectionType = SelectionType.Normal,
) : SelectionEvent {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TableSelectionEvent

        if (columnKey != other.columnKey) return false
        if (rowKey != other.rowKey) return false
        if (selectionUnit != other.selectionUnit) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = columnKey?.hashCode() ?: 0
        result = 31 * result + (rowKey?.hashCode() ?: 0)
        result = 31 * result + selectionUnit.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString(): String =
        "TableSelectionEvent(columnKey=$columnKey, rowKey=$rowKey, selectionUnit=$selectionUnit, type=$type)"
}
