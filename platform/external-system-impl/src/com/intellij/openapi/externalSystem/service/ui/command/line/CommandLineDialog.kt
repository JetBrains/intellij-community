// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.command.line

import com.intellij.ide.IdeCoreBundle
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.RecursionManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.ListSelectionModel

@ApiStatus.Internal
class CommandLineDialog(
  project: Project,
  private val commandLineField: CommandLineField,
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

  private fun clearSelectionWhenSelected(tableToUpdate: JTable, tableToListen: JTable) {
    tableToListen.selectionModel.addListSelectionListener {
      selectRecursionGuard.doPreventingRecursion(this, false) {
        tableToUpdate.clearSelection()
      }
    }
  }

  override fun createCenterPanel() = panel {
    val tables = commandLineInfo.tablesInfo.map { Table(it, commandLineField) }

    for ((i, tableToUpdate) in tables.withIndex()) {
      for ((j, tableToListen) in tables.withIndex()) {
        if (i != j) {
          clearSelectionWhenSelected(tableToUpdate, tableToListen)
        }
      }
    }

    for (table in tables) {
      row {
        scrollCell(table).align(Align.FILL)
      }.resizableRow()
    }

    onApply {
      val selectedVariant = tables.firstNotNullOfOrNull { it.selectedVariant }
      fireVariantChosen(selectedVariant)
    }
  }

  init {
    title = commandLineInfo.dialogTitle
    setOKButtonText(IdeCoreBundle.message("button.insert"))
    init()
  }

  private class Table(tableInfo: CompletionTableInfo, commandLineField: CommandLineField) : JBTable() {

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

    init {
      val completionProgressOwner = ModalTaskOwner.component(commandLineField)
      val completionProgressTitle = ExternalSystemBundle.message("command.line.dialog.completion.collecting")
      val completionInfo = runWithModalProgressBlocking(completionProgressOwner, completionProgressTitle) {
        tableInfo.collectTableCompletionInfo()
      }
      val dataColumnInfo = column(tableInfo.dataColumnName) { it.text }
      val descriptionColumnInfo = column(tableInfo.descriptionColumnName) { it.description ?: "" }
      model = ListTableModel(arrayOf(dataColumnInfo, descriptionColumnInfo), completionInfo)
    }

    init {
      val nameColumn = columnModel.getColumn(0)
      val descriptionColumn = columnModel.getColumn(1)

      val search = TableSpeedSearch.installOn(this)
      nameColumn.cellRenderer = Renderer(search, tableInfo.dataColumnIcon)
      descriptionColumn.cellRenderer = Renderer(search, tableInfo.descriptionColumnIcon)

      visibleRowCount = 8
      nameColumn.preferredWidth = JBUIScale.scale(250)
      descriptionColumn.preferredWidth = JBUIScale.scale(500)
    }

    companion object {
      private fun column(@NlsContexts.ColumnName name: String, valueOf: (TextCompletionInfo) -> String) =
        object : ColumnInfo<TextCompletionInfo, String>(name) {
          override fun valueOf(item: TextCompletionInfo) = valueOf(item)
        }
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