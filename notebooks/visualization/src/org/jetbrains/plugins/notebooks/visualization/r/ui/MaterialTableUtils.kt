/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.util.ui.JBUI
import javax.swing.JTable
import kotlin.math.max
import kotlin.math.min

object MaterialTableUtils {

  fun getColumnHeaderWidth(table: JTable, column: Int): Int {

    if (table.tableHeader == null || table.columnModel.columnCount <= column) {
      return 0
    }

    val tableColumn = table.columnModel.getColumn(column)
    var renderer = tableColumn.headerRenderer

    if (renderer == null) {
      renderer = table.tableHeader.defaultRenderer
    }

    val value = tableColumn.headerValue
    val c = renderer!!.getTableCellRendererComponent(table, value, false, false, -1, column)
    return c.preferredSize.width
  }

  fun getColumnWidth(table: JTable, column: Int, maxRows: Int? = null): Int {
    var width = max(JBUI.scale(65), getColumnHeaderWidth(table, column)) // Min width

    // We should not cycle through all
    for (row in 0 until min(table.rowCount, maxRows ?: Int.MAX_VALUE)) {
      val renderer = table.getCellRenderer(row, column)
      val comp = table.prepareRenderer(renderer, row, column)
      width = max(comp.preferredSize.width + JBUI.scale(10), width)
    }

    return width
  }

  fun fitColumnWidth(column: Int, table: JTable, maxWidth: Int = 350, maxRows: Int? = null) {
    var width = getColumnWidth(table, column, maxRows)

    if (maxWidth in 1 until width) {
      width = maxWidth
    }

    table.columnModel.getColumn(column).preferredWidth = width
  }

  /**
   * Fits the table columns widths by guideline https://jetbrains.github.io/ui/controls/table/
   */
  fun fitColumnsWidth(table: JTable, maxWidth: Int = 350, maxRows: Int? = null) {

    for (column in 0 until table.columnCount) {
      fitColumnWidth(column, table, maxWidth, maxRows)
    }
  }

  private fun getRowHeight(table: JTable): Int {
    val column = 0
    val row = 0
    val renderer = table.getCellRenderer(row, column)
    val component = table.prepareRenderer(renderer, row, column)
    return component.preferredSize.height
  }

  fun fitRowHeight(table: JTable) {
    table.tableHeader.resizeAndRepaint()
    table.rowHeight = getRowHeight(table)
  }
}