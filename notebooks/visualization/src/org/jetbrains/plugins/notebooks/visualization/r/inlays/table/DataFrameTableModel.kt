/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table

import org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.DataFrame
import javax.swing.table.AbstractTableModel

class DataFrameTableModel(val dataFrame: DataFrame) : AbstractTableModel() {

    override fun getRowCount(): Int {
        return dataFrame.dim.height
    }

    override fun getColumnCount(): Int {
        return dataFrame.dim.width
    }

    override fun getColumnName(columnIndex: Int): String {
        return dataFrame[columnIndex].name
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        return dataFrame[columnIndex].toList()[rowIndex]
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return false
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        throw NotImplementedError()
    }
}