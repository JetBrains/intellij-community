// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings.ExternalToolConfiguration
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

internal class ExternalToolsTablePanel : BorderLayoutPanel() {
  private val columnInfos = arrayOf(FileTypeColumn(), ExternalDiffToolColumn(), ExternalMergeToolColumn())

  val model: ListTableModel<ExternalToolConfiguration> = ListTableModel<ExternalToolConfiguration>(columnInfos, mutableListOf(), 0)

  private val table: TableView<ExternalToolConfiguration> = TableView(model).apply {
    visibleRowCount = 8
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(mouseEvent: MouseEvent) {
        if (mouseEvent.clickCount == 2 && selectedRow != -1) {
          editData()
        }
      }
    })
  }

  val component: JComponent

  init {
    val toolbarTable = ToolbarDecorator.createDecorator(table)
      .setAddAction { addData() }
      .setRemoveAction { removeData() }
      .setEditAction { editData() }
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
    model.addRow(settings.defaultToolConfiguration)
    model.addRows(settings.externalToolsConfiguration)
  }

  private fun addData() {
    val dialog = AddToolConfigurationDialog()
    if (dialog.showAndGet()) {
      model.addRow(dialog.createExternalToolConfiguration())
    }
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

  private fun editData() {
    val selectedRow = table.selectedRow
    val selectedToolConfiguration = model.getItem(selectedRow)
    val dialog = AddToolConfigurationDialog(selectedToolConfiguration)

    if (dialog.showAndGet()) {
      val toolConfiguration = dialog.createExternalToolConfiguration()

      model.removeRow(selectedRow)
      model.insertRow(selectedRow, toolConfiguration)
    }
  }

  private class FileTypeColumn : ColumnInfo<ExternalToolConfiguration, String>(
    DiffBundle.message("settings.external.diff.table.filetype.column")
  ) {
    override fun valueOf(externalToolConfiguration: ExternalToolConfiguration): String {
      return externalToolConfiguration.fileTypeName
    }

    override fun getRenderer(item: ExternalToolConfiguration): TableCellRenderer {
      return object : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
          if (item.fileTypeName == ExternalToolConfiguration.DEFAULT_TOOL_NAME) {
            append(DiffBundle.message("settings.external.diff.table.default.tool.name"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            return
          }

          val fileTypeManager = FileTypeManager.getInstance()
          val fileType = fileTypeManager.findFileTypeByName(item.fileTypeName)

          icon = fileType.icon
          append(fileType.description)
        }
      }
    }
  }

  private class ExternalDiffToolColumn : ColumnInfo<ExternalToolConfiguration, String>(
    DiffBundle.message("settings.external.diff.table.difftool.column")
  ) {
    override fun valueOf(externalToolConfiguration: ExternalToolConfiguration): String {
      return externalToolConfiguration.diffToolName
    }

    override fun getRenderer(item: ExternalToolConfiguration): TableCellRenderer {
      return ToolCellRenderer(item.diffToolName)
    }
  }

  private class ExternalMergeToolColumn : ColumnInfo<ExternalToolConfiguration, String>(
    DiffBundle.message("settings.external.diff.table.mergetool.column")
  ) {
    override fun valueOf(externalToolConfiguration: ExternalToolConfiguration): String {
      return externalToolConfiguration.mergeToolName
    }

    override fun getRenderer(item: ExternalToolConfiguration): TableCellRenderer {
      return ToolCellRenderer(item.mergeToolName)
    }
  }

  private class ToolCellRenderer(private val toolName: String) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      if (toolName == ExternalToolConfiguration.BUILTIN_TOOL) {
        append(DiffBundle.message("settings.external.diff.table.default.tool.builtin"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        return
      }

      append(toolName) // NON-NLS
    }
  }

  private inner class AddToolConfigurationDialog(private val oldFileType: String? = null) : DialogWrapper(null) {
    private val fileTypeManager = FileTypeManager.getInstance()
    private val fileTypes = fileTypeManager.registeredFileTypes.map { it.name }.toList()

    private val builtinTool = ExternalDiffSettings.ExternalTool(ExternalToolConfiguration.BUILTIN_TOOL)
    private val tools = ExternalDiffSettings.instance.externalTools
    private val externalDiffTools = mutableListOf(builtinTool).apply {
      tools[ExternalDiffSettings.ExternalToolGroup.DIFF_TOOL]?.let { addAll(it) }
    }
    private val externalMergeTools = mutableListOf(builtinTool).apply {
      tools[ExternalDiffSettings.ExternalToolGroup.MERGE_TOOL]?.let { addAll(it) }
    }

    private val fileTypeField = ComboBox(fileTypes.toTypedArray()).apply {
      renderer = FileTypeCellRenderer()
    }
    private val diffToolField = ComboBox(externalDiffTools.toTypedArray()).apply {
      renderer = ExternalToolCellRenderer()
    }
    private val mergeToolField = ComboBox(externalMergeTools.toTypedArray()).apply {
      renderer = ExternalToolCellRenderer()
    }

    private lateinit var fileTypeRow: Row

    constructor(externalToolConfiguration: ExternalToolConfiguration) : this(externalToolConfiguration.fileTypeName) {
      if (externalToolConfiguration.fileTypeName == ExternalToolConfiguration.DEFAULT_TOOL_NAME) {
        customizeWithDefaultTool(externalToolConfiguration)
        return
      }

      fileTypeField.selectedItem = externalToolConfiguration.fileTypeName // NON-NLS
      diffToolField.selectedItem = externalDiffTools.find { it.name == externalToolConfiguration.diffToolName }
      mergeToolField.selectedItem = externalMergeTools.find { it.name == externalToolConfiguration.mergeToolName }
    }

    init {
      JBUI.size(400, 200).let {
        rootPane.minimumSize = it
        rootPane.preferredSize = it
      }

      title = DiffBundle.message("settings.external.diff.table.add.dialog.title")
      init()
    }

    override fun createCenterPanel(): JComponent = panel {
      fileTypeRow = row(DiffBundle.message("settings.external.diff.table.add.dialog.filetype")) {
        cell(fileTypeField).horizontalAlign(HorizontalAlign.FILL).validationOnApply { fileTypeValidation(it.item) }
      }
      row(DiffBundle.message("settings.external.diff.table.add.dialog.difftool")) {
        cell(diffToolField).horizontalAlign(HorizontalAlign.FILL).validationOnApply { comboBoxValidation(it.item) }
      }
      row(DiffBundle.message("settings.external.diff.table.add.dialog.mergetool")) {
        cell(mergeToolField).horizontalAlign(HorizontalAlign.FILL).validationOnApply { comboBoxValidation(it.item) }
      }
    }

    fun createExternalToolConfiguration(): ExternalToolConfiguration = ExternalToolConfiguration(fileTypeField.item,
                                                                                                 diffToolField.item.name,
                                                                                                 mergeToolField.item.name)

    private fun customizeWithDefaultTool(externalToolConfiguration: ExternalToolConfiguration) {
      fileTypeField.removeAllItems()
      fileTypeField.addItem(ExternalToolConfiguration.DEFAULT_TOOL_NAME) // NON-NLS
      fileTypeField.item = ExternalToolConfiguration.DEFAULT_TOOL_NAME // NON-NLS

      diffToolField.selectedItem = getExternalToolOrDefault(externalToolConfiguration.diffToolName, externalDiffTools)
      mergeToolField.selectedItem = getExternalToolOrDefault(externalToolConfiguration.mergeToolName, externalMergeTools)

      fileTypeRow.visible(false)
    }

    private fun fileTypeValidation(fileTypeName: String): ValidationInfo? {
      model.fireTableDataChanged()
      if (model.items.any { it.fileTypeName == fileTypeName } && fileTypeName != oldFileType) {
        return ValidationInfo(DiffBundle.message("settings.external.diff.table.validation.filetype.registered"))
      }

      return null
    }

    private fun comboBoxValidation(externalTool: ExternalDiffSettings.ExternalTool?): ValidationInfo? {
      if (externalTool == null) {
        return ValidationInfo(DiffBundle.message("settings.external.diff.table.validation.empty"))
      }

      return null
    }

    private fun getExternalToolOrDefault(toolName: String,
                                         tools: List<ExternalDiffSettings.ExternalTool>): ExternalDiffSettings.ExternalTool? {
      return if (toolName == ExternalToolConfiguration.BUILTIN_TOOL) builtinTool
      else tools.find { tool -> tool.name == toolName }
    }

    private inner class FileTypeCellRenderer : ColoredListCellRenderer<String>() {
      override fun customizeCellRenderer(list: JList<out String>,
                                         value: String,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        if (value == ExternalToolConfiguration.DEFAULT_TOOL_NAME) {
          append(DiffBundle.message("settings.external.diff.table.default.tool.name"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          return
        }

        val fileType = fileTypeManager.findFileTypeByName(value)
        if (fileType == null) return

        icon = fileType.icon
        append(fileType.description)
      }
    }

    private inner class ExternalToolCellRenderer : ColoredListCellRenderer<ExternalDiffSettings.ExternalTool>() {
      override fun customizeCellRenderer(list: JList<out ExternalDiffSettings.ExternalTool>,
                                         value: ExternalDiffSettings.ExternalTool?,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        if (value == null) {
          append("")
          return
        }

        if (value.name == ExternalToolConfiguration.BUILTIN_TOOL) {
          append(DiffBundle.message("settings.external.diff.table.default.tool.builtin"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          return
        }

        append(value.name) // NON-NLS
      }
    }
  }
}