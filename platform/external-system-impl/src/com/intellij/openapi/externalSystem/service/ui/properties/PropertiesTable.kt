// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.properties

import com.intellij.execution.util.ListTableWithButtons
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesTable.Property
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.whenTableChanged
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBViewport
import com.intellij.ui.scale.JBUIScale
import com.intellij.openapi.observable.util.lockOrSkip
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.DefaultCellEditor
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import kotlin.reflect.KMutableProperty1

class PropertiesTable : ListTableWithButtons<Property>() {

  var properties: List<Property>
    get() = elements.cleanupProperties()
    set(properties) = setValues(properties.cleanupProperties())

  private fun List<Property>.cleanupProperties() =
    map { Property(it.name.trim(), it.value.trim()) }
      .filterNot { it.name.isEmpty() && it.value.isEmpty() }

  fun bindProperties(property: ObservableMutableProperty<List<Property>>) = apply {
    val mutex = AtomicBoolean()
    mutex.lockOrSkip {
      properties = property.get()
    }
    tableView.whenTableChanged {
      mutex.lockOrSkip {
        property.set(properties)
      }
    }
    property.afterChange {
      mutex.lockOrSkip {
        properties = it
      }
    }
  }

  init {
    val nameColumn = tableView.columnModel.getColumn(0)
    val descriptionColumn = tableView.columnModel.getColumn(1)

    val search = TableSpeedSearch(tableView)
    nameColumn.cellRenderer = Renderer(search)
    descriptionColumn.cellRenderer = Renderer(search)

    tableView.visibleRowCount = 8
    tableView.putClientProperty(JBViewport.FORCE_VISIBLE_ROW_COUNT_KEY, true)
    tableView.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    tableView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    tableView.rowSelectionAllowed = true
    tableView.columnSelectionAllowed = true
    tableView.setShowGrid(false)
    nameColumn.preferredWidth = JBUIScale.scale(225)
    descriptionColumn.preferredWidth = JBUIScale.scale(225)
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

  private class Renderer(private val search: TableSpeedSearch) : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
      val text = value as? String ?: return
      SearchUtil.appendFragments(search.enteredPrefix, text, SimpleTextAttributes.STYLE_PLAIN, null, null, this)
    }
  }

  data class Property(var name: String, var value: String)
}