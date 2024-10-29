// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui.table

import com.intellij.analysis.AnalysisBundle
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.ui.OffsetIcon
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.table.IconTableCellRenderer
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
class ScopesOrderTable : TableView<NamedScope>() {
  private val model = ListTableModel<NamedScope>(IconColumn, NameColumn, SetColumn)

  init {
    setModelAndUpdateColumns(model)
    rowSelectionAllowed = true
    columnSelectionAllowed = false
    setShowGrid(false)
    tableHeader.reorderingAllowed = false
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    getColumnModel().getColumn(0).apply {
      maxWidth = JBUI.scale(34)
      minWidth = JBUI.scale(34)
    }
    getColumnModel().getColumn(1).apply {
      preferredWidth = JBUI.scale(200)
    }
  }

  fun updateItems(scopes: Collection<NamedScope>) {
    while (model.rowCount > 0) model.removeRow(0)
    model.addRows(scopes)
  }

  fun getScopeAt(i: Int): NamedScope? {
    return model.getItem(i)
  }

  fun moveUp() {
    if (selectedRow > 0) move(-1)
  }

  fun moveDown() {
    if (selectedRow + 1 < rowCount) move(1)
  }

  private fun move(offset: Int) {
    val selected = selectedRow
    model.exchangeRows(selected, selected + offset)
    selectionModel.apply {
      clearSelection()
      addSelectionInterval(selected + offset, selected + offset)
    }
    scrollRectToVisible(getCellRect(selectedRow, 0, true))
    repaint()
  }

  private object IconColumn : ColumnInfo<NamedScope, String?>("") {
    override fun valueOf(item: NamedScope): String = ""

    override fun getRenderer(item: NamedScope?): TableCellRenderer {
      return object : IconTableCellRenderer<String>() {
        override fun getIcon(value: String, table: JTable?, row: Int): Icon? {
          when (item?.icon) {
            is OffsetIcon -> return (item.icon as OffsetIcon).icon
            else -> return item?.icon
          }
        }

        override fun isCenterAlignment(): Boolean = true
      }
    }
  }

  private object NameColumn : ColumnInfo<NamedScope, String>(AnalysisBundle.message("inspections.settings.scopes.name")) {
    override fun valueOf(item: NamedScope): String = item.presentableName
  }

  private object SetColumn : ColumnInfo<NamedScope, String>(AnalysisBundle.message("inspections.settings.scopes.pattern")) {
    override fun valueOf(item: NamedScope): String = item.value?.text ?: ""
  }

}