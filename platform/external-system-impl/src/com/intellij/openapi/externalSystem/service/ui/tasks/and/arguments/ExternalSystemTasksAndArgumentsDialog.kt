// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionContributor.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments.ExternalSystemTasksAndArgumentsInfo.CompletionTableInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.RecursionManager
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.layout.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.ListSelectionModel


@ApiStatus.Experimental
class ExternalSystemTasksAndArgumentsDialog(
  project: Project,
  private val tasksAndArgumentsInfo: ExternalSystemTasksAndArgumentsInfo
) : DialogWrapper(project) {

  private val selectRecursionGuard =
    RecursionManager.createGuard<ExternalSystemTasksAndArgumentsDialog>(ExternalSystemTasksAndArgumentsDialog::class.java.name)

  private val chooseListeners = CopyOnWriteArrayList<(TextCompletionInfo) -> Unit>()

  fun whenVariantChosen(listener: (TextCompletionInfo) -> Unit) {
    chooseListeners.add(listener)
  }

  private fun fireVariantChosen(item: TextCompletionInfo?) {
    if (item != null) {
      chooseListeners.forEach { it(item) }
    }
  }

  fun JTable.clearSelectionWhenSelected(table: JTable) {
    table.selectionModel.addListSelectionListener {
      selectRecursionGuard.doPreventingRecursion(this@ExternalSystemTasksAndArgumentsDialog, false) {
        clearSelection()
      }
    }
  }

  override fun createCenterPanel() = panel {
    val tables = tasksAndArgumentsInfo.tablesInfo
      .filter { it.tableCompletionInfo.isNotEmpty() }
      .map { Table(it) }

    for ((i, parent) in tables.withIndex()) {
      for ((j, child) in tables.withIndex()) {
        if (i != j) {
          parent.clearSelectionWhenSelected(child)
        }
      }
    }

    for (table in tables) {
      row {
        scrollPane(table)
      }
    }

    onGlobalApply {
      val selectedVariant = tables
        .mapNotNull { it.selectedVariant }
        .firstOrNull()
      fireVariantChosen(selectedVariant)
    }
  }

  init {
    title = tasksAndArgumentsInfo.title
    setOKButtonText(IdeBundle.message("button.insert"))
    init()
  }

  private inner class Table(tableInfo: CompletionTableInfo) : JBTable() {
    val selectedVariant: TextCompletionInfo?
      get() {
        if (selectedRow != -1) {
          @Suppress("UNCHECKED_CAST")
          val model = model as ListTableModel<TextCompletionInfo>
          return model.getItem(selectedRow)
        }
        else {
          return null
        }
      }

    private fun setEmptyText(@Nls text: String) {
      getAccessibleContext().accessibleName = text
      emptyText.text = text
    }

    init {
      setEmptyText(tableInfo.emptyState)
    }

    init {
      setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS)
      setRowSelectionAllowed(true)
      columnSelectionAllowed = false
      dragEnabled = false
      setShowGrid(false)
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }

    private fun column(@NlsContexts.ColumnName name: String, valueOf: (TextCompletionInfo) -> String) =
      object : ColumnInfo<TextCompletionInfo, String>(name) {
        override fun valueOf(item: TextCompletionInfo) = valueOf(item)
      }

    init {
      val dataColumnInfo = column(tableInfo.dataColumnName) { it.text }
      val descriptionColumnInfo = column(tableInfo.descriptionColumnName) { it.description ?: "" }
      model = ListTableModel(arrayOf(dataColumnInfo, descriptionColumnInfo), tableInfo.tableCompletionInfo)
    }

    init {
      val search = TableSpeedSearch(this)
      val nameColumn = columnModel.getColumn(0)
      nameColumn.cellRenderer = Renderer(search, tableInfo.dataIcon)
      val descriptionColumn = columnModel.getColumn(1)
      descriptionColumn.cellRenderer = Renderer(search, null)
    }
  }

  private class Renderer(private val search: TableSpeedSearch, private val cellIcon: Icon?) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      val text = value as? String ?: return
      icon = cellIcon
      isTransparentIconBackground = true
      SearchUtil.appendFragments(search.enteredPrefix, text, SimpleTextAttributes.STYLE_PLAIN, null, null, this)
    }
  }
}