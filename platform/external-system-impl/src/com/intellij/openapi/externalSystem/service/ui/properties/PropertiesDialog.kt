// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.properties

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesFiled.Property
import com.intellij.openapi.externalSystem.service.ui.util.ObservableDialogWrapper
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBViewport
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import javax.swing.DefaultCellEditor
import javax.swing.JTable
import javax.swing.JTextField
import kotlin.reflect.KMutableProperty1

class PropertiesDialog(
  private val project: Project,
  private val info: PropertiesInfo
) : ObservableDialogWrapper(project) {

  private val table = Table(info)

  var properties by table::properties

  override fun configureCenterPanel(panel: Panel) {
    with(panel) {
      row {
        label(info.dialogLabel)
      }
      row {
        cell(table.component)
          .horizontalAlign(HorizontalAlign.FILL)
      }
    }
  }

  override fun doOKAction() {
    val validationInfo = validateProperties()
    if (validationInfo != null) {
      val title = ExternalSystemBundle.message("external.system.properties.error.title")
      Messages.showErrorDialog(project, validationInfo.message, title)
      return
    }
    super.doOKAction()
  }

  private fun validateProperties(): ValidationInfo? {
    if (table.properties.any { it.name.isEmpty() }) {
      return ValidationInfo(ExternalSystemBundle.message("external.system.properties.error.empty.message"))
    }
    if (table.properties.any { it.name.contains(' ') }) {
      return ValidationInfo(ExternalSystemBundle.message("external.system.properties.error.space.message"))
    }
    if (table.properties.any { it.name.contains('=') }) {
      return ValidationInfo(ExternalSystemBundle.message("external.system.properties.error.assign.message"))
    }
    return null
  }

  init {
    title = info.dialogTitle
    setOKButtonText(info.dialogOkButton)
    init()
  }

  private class Table(info: PropertiesInfo) : ListTableWithButtons<Property>() {

    var properties: List<Property>
      get() = elements.cleanupProperties()
      set(properties) = setValues(properties.cleanupProperties())

    private fun List<Property>.cleanupProperties() =
      map { Property(it.name.trim(), it.value.trim()) }
        .filterNot { it.name.isEmpty() && it.value.isEmpty() }

    init {
      tableView.getAccessibleContext().accessibleName = info.dialogEmptyState
      tableView.emptyText.text = info.dialogEmptyState
    }

    init {
      tableView.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
      tableView.setShowGrid(false)
    }

    init {
      val nameColumn = tableView.columnModel.getColumn(0)
      val descriptionColumn = tableView.columnModel.getColumn(1)

      val search = TableSpeedSearch(tableView)
      nameColumn.cellRenderer = Renderer(search)
      descriptionColumn.cellRenderer = Renderer(search)

      tableView.visibleRowCount = 8
      UIUtil.putClientProperty(tableView, JBViewport.FORCE_VISIBLE_ROW_COUNT_KEY, true)
      nameColumn.preferredWidth = JBUIScale.scale(225)
      descriptionColumn.preferredWidth = JBUIScale.scale(225)

      nameColumn.cellEditor
    }

    override fun createListModel(): ListTableModel<Property> {
      val nameColumnInfo = column(Property::name)
      val valueColumnInfo = column(Property::value)
      return ListTableModel(nameColumnInfo, valueColumnInfo)
    }

    private fun column(property: KMutableProperty1<Property, String>) =
      object : ColumnInfo<Property, String>(null) {
        override fun valueOf(item: Property) = property.get(item)
        override fun isCellEditable(item: Property) = true
        override fun getEditor(item: Property) = DefaultCellEditor(JTextField())
        override fun setValue(item: Property, value: String) = property.set(item, value)
      }

    override fun createElement(): Property = Property("", "")

    override fun isEmpty(element: Property): Boolean = element.name.isEmpty()

    override fun cloneElement(variable: Property) = Property(variable.name, variable.value)

    override fun canDeleteElement(selection: Property): Boolean = true
  }

  private class Renderer(private val search: TableSpeedSearch) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      val text = value as? String ?: return
      SearchUtil.appendFragments(search.enteredPrefix, text, SimpleTextAttributes.STYLE_PLAIN, null, null, this)
    }
  }
}