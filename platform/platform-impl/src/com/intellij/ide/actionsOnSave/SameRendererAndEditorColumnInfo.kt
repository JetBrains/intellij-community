// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.table.TableView
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal abstract class SameRendererAndEditorColumnInfo<T : Any>(@NlsContexts.ColumnName columnName: String) : ColumnInfo<T, T>(columnName) {

  companion object {
    const val LEFT_RIGHT_INSET: Int = 8

    fun setupTableCellBackground(component: JComponent, hovered: Boolean) {
      val bgColor = if (hovered)
        JBUI.CurrentTheme.Table.Hover.background(true)
      else
        UIUtil.getTableBackground(false, false)
      UIUtil.setOpaqueRecursively(component, false)
      component.isOpaque = true
      component.background = bgColor
    }
  }

  private val cellRenderer = TableCellRenderer { table, value, _, _, row, _ ->
    val hovered = TableHoverListener.getHoveredRow(table) == row

    @Suppress("UNCHECKED_CAST")
    getCellComponent(table as TableView<*>, value as T, hovered)
  }

  private val tableCellEditor = object : AbstractTableCellEditor() {
    override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
      @Suppress("UNCHECKED_CAST")
      return getCellComponent(table as TableView<*>, value as T, true)
    }

    override fun getCellEditorValue(): Any? = null
  }

  override fun valueOf(value: T): T = value

  override fun isCellEditable(value: T): Boolean = true

  override fun getRenderer(value: T): TableCellRenderer = cellRenderer

  override fun getEditor(value: T): TableCellEditor = tableCellEditor

  protected abstract fun getCellComponent(table: TableView<*>, info: T, hovered: Boolean): JComponent
}
