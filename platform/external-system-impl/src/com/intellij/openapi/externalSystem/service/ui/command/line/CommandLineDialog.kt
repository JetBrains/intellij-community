// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.command.line

import com.intellij.ide.IdeCoreBundle
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.RecursionManager
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.layout.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.ListSelectionModel

class CommandLineDialog(
  project: Project,
  private val commandLineInfo: CommandLineInfo
) : DialogWrapper(project) {

  private val selectRecursionGuard =
    RecursionManager.createGuard<CommandLineDialog>(CommandLineDialog::class.java.name)

  private val chooseListeners = CopyOnWriteArrayList<(TextCompletionInfo) -> Unit>()

  fun whenVariantChosen(listener: (TextCompletionInfo) -> Unit) {
    chooseListeners.add(listener)
  }

  private fun fireVariantChosen(item: TextCompletionInfo?) {
    if (item != null) {
      chooseListeners.forEach { it(item) }
    }
  }

  fun clearSelectionWhenSelected(tableToUpdate: JTable, tableToListen: JTable) {
    tableToListen.selectionModel.addListSelectionListener {
      selectRecursionGuard.doPreventingRecursion(this, false) {
        tableToUpdate.clearSelection()
      }
    }
  }

  override fun createCenterPanel() = panel {
    val tables = commandLineInfo.tablesInfo
      .filter { it.tableCompletionInfo.isNotEmpty() }
      .map { Table(it) }

    for ((i, tableToUpdate) in tables.withIndex()) {
      for ((j, tableToListen) in tables.withIndex()) {
        if (i != j) {
          clearSelectionWhenSelected(tableToUpdate, tableToListen)
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
    title = commandLineInfo.dialogTitle
    setOKButtonText(IdeCoreBundle.message("button.insert"))
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

    init {
      setEmptyState(tableInfo.emptyState)
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
      val nameColumn = columnModel.getColumn(0)
      val descriptionColumn = columnModel.getColumn(1)

      val search = TableSpeedSearch(this)
      nameColumn.cellRenderer = Renderer(search, tableInfo.dataColumnIcon)
      descriptionColumn.cellRenderer = Renderer(search, tableInfo.descriptionColumnIcon)

      visibleRowCount = 8
      nameColumn.preferredWidth = JBUIScale.scale(250)
      descriptionColumn.preferredWidth = JBUIScale.scale(500)
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