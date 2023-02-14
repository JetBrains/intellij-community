/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table

import org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.DataFrame
import javax.swing.table.AbstractTableModel
import kotlin.math.ceil
import kotlin.math.min

class PagingTableModel(val dataFrame: DataFrame) : AbstractTableModel() {

    var pageSize = 10
        set(value) {
            if (value == field) {
                return
            }
            val oldPageSize = field
            field = value
            pageOffset = oldPageSize * pageOffset / field
            fireTableDataChanged()
        }

    var pageOffset = 0
        set(value) {
            field = when {
                value <0 -> 0
                value >= getPageCount() -> getPageCount()-1
                else -> value
            }
            fireTableDataChanged()
        }

    override fun getRowCount(): Int {
        return min(dataFrame.dim.height - pageOffset * pageSize, pageSize)
    }

    override fun getColumnCount(): Int {
        return dataFrame.dim.width
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val realRow = rowIndex + pageOffset * pageSize
        return dataFrame[columnIndex].toList()[realRow]
    }

    override fun getColumnName(columnIndex: Int): String {
        return dataFrame[columnIndex].name
    }

    fun getPageCount(): Int {
        return ceil(dataFrame.dim.height / pageSize.toDouble()).toInt()
    }

    fun getRealRowCount(): Int {
        return dataFrame.dim.height
    }
}