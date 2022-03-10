// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolConfiguration
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolGroup
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class ExternalToolsTablePanel : BorderLayoutPanel() {
  val model = ListTableModel<ExternalToolConfiguration>(FileTypeColumn(),
                                                        ExternalToolColumn(ExternalToolGroup.DIFF_TOOL),
                                                        ExternalToolColumn(ExternalToolGroup.MERGE_TOOL))
  val component: JComponent

  private val table: TableView<ExternalToolConfiguration> = TableView(model).apply {
    visibleRowCount = 8
  }

  init {
    val toolbarTable = ToolbarDecorator.createDecorator(table)
      .setAddAction { addData() }
      .setRemoveAction { removeData() }
      .disableUpDownActions()
      .createPanel()

    component = toolbarTable
  }

  fun onModified(settings: ExternalDiffSettings): Boolean {
    val persistedData = mutableListOf(settings.defaultToolConfiguration).apply {
      addAll(settings.externalToolsConfiguration)
    }

    return model.items != persistedData
  }

  fun onApply(settings: ExternalDiffSettings) {
    val persistedData = model.items
    settings.defaultToolConfiguration = persistedData[0]

    if (persistedData.size > 1) {
      settings.externalToolsConfiguration = persistedData.subList(1, persistedData.size)
    }
    else {
      settings.externalToolsConfiguration = mutableListOf()
    }
  }

  fun onReset(settings: ExternalDiffSettings) {
    repeat(model.items.size) { model.removeRow(0) }
    model.addRow(settings.defaultToolConfiguration.copy())
    model.addRows(settings.externalToolsConfiguration.map { it.copy() })
  }

  private fun addData() {
    model.addRow(ExternalToolConfiguration(""))
  }

  private fun removeData() {
    if (model.getItem(table.selectedRow).fileTypeName == ExternalToolConfiguration.DEFAULT_TOOL_NAME) {
      return
    }

    val dialog = MessageDialogBuilder.okCancel(DiffBundle.message("settings.external.diff.table.remove.dialog.title"),
                                               DiffBundle.message("settings.external.diff.table.remove.dialog.message"))
    if (dialog.guessWindowAndAsk()) {
      model.removeRow(table.selectedRow)
    }
  }

  class FileTypeColumn : ColumnInfo<ExternalToolConfiguration, String>(
    DiffBundle.message("settings.external.diff.table.filetype.column")
  ) {
    private val values = FileTypeManager.getInstance().registeredFileTypes.map { it.displayName }
    private val comboBoxCellRendererAndEditor = FileTypeCellComboBox(values).withClickCount(1)

    override fun valueOf(externalToolConfiguration: ExternalToolConfiguration): String {
      return externalToolConfiguration.fileTypeName
    }

    override fun setValue(item: ExternalToolConfiguration, value: String) {
      item.fileTypeName = value
    }

    override fun getEditor(item: ExternalToolConfiguration): TableCellEditor = comboBoxCellRendererAndEditor

    override fun getRenderer(item: ExternalToolConfiguration): TableCellRenderer = comboBoxCellRendererAndEditor

    override fun isCellEditable(item: ExternalToolConfiguration): Boolean = true

    private class FileTypeCellComboBox(values: List<String>) : ComboBoxTableRenderer<String>(values.toTypedArray()) {
      private val fileTypeManager = FileTypeManager.getInstance()

      override fun getTableCellRendererComponent(table: JTable, value: Any,
                                                 isSelected: Boolean, hasFocus: Boolean,
                                                 row: Int, column: Int): Component {
        if ((value as String) == ExternalToolConfiguration.DEFAULT_TOOL_NAME) {
          return SimpleColoredComponent().apply {
            append(value, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) // NON-NLS
          }
        }

        return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
      }

      override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component? {
        if ((value as String) == ExternalToolConfiguration.DEFAULT_TOOL_NAME) {
          return null
        }

        return super.getTableCellEditorComponent(table, value, isSelected, row, column)
      }

      override fun getIconFor(value: String): Icon? = fileTypeManager.findFileTypeByName(value)?.icon
    }
  }

  class ExternalToolColumn(private val externalToolGroup: ExternalToolGroup) : ColumnInfo<ExternalToolConfiguration, String>(
    DiffBundle.message("settings.external.diff.table.difftool.column")
  ) {
    override fun valueOf(externalToolConfiguration: ExternalToolConfiguration): String = when (externalToolGroup) {
      ExternalToolGroup.DIFF_TOOL -> externalToolConfiguration.diffToolName
      ExternalToolGroup.MERGE_TOOL -> externalToolConfiguration.mergeToolName
    }

    override fun setValue(item: ExternalToolConfiguration, value: String) {
      when (externalToolGroup) {
        ExternalToolGroup.DIFF_TOOL -> item.diffToolName = value
        ExternalToolGroup.MERGE_TOOL -> item.mergeToolName = value
      }
    }

    override fun getRenderer(item: ExternalToolConfiguration): TableCellRenderer {
      val values = ExternalDiffSettings.collectExternalToolNames(externalToolGroup).toTypedArray()
      return ComboBoxTableRenderer(values).withClickCount(1)
    }

    override fun getEditor(item: ExternalToolConfiguration): TableCellEditor {
      val values = ExternalDiffSettings.collectExternalToolNames(externalToolGroup).toTypedArray()
      return ComboBoxTableRenderer(values).withClickCount(1)
    }

    override fun isCellEditable(item: ExternalToolConfiguration): Boolean = true
  }
}