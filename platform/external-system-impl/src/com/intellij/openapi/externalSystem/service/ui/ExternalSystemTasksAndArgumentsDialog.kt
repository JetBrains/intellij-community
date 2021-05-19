// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.RecursionManager
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.layout.*
import com.intellij.ui.table.JBTable
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.ListSelectionModel

class ExternalSystemTasksAndArgumentsDialog(
  project: Project,
  private val tasksAndArguments: ExternalSystemTasksAndArguments
) : DialogWrapper(project) {

  private val selectRecursionGuard =
    RecursionManager.createGuard<ExternalSystemTasksAndArgumentsDialog>(ExternalSystemTasksAndArgumentsDialog::class.java.name)

  private val itemChooseListeners = CopyOnWriteArrayList<(Item) -> Unit>()

  fun whenItemChosen(listener: (Item) -> Unit) {
    itemChooseListeners.add(listener)
  }

  private fun fireItemChosen(item: Item?) {
    if (item != null) {
      itemChooseListeners.forEach { it(item) }
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
    val taskTable = Table(
      ExternalSystemBundle.message("run.configuration.tasks.and.arguments.task.column"),
      tasksAndArguments.tasks.map { Item(it.name, it.description, AllIcons.General.Gear) }
    )
    val argumentTable = Table(
      ExternalSystemBundle.message("run.configuration.tasks.and.arguments.argument.column"),
      tasksAndArguments.arguments.map { Item(it.name, it.description) }
    )

    taskTable.clearSelectionWhenSelected(argumentTable)
    argumentTable.clearSelectionWhenSelected(taskTable)

    onGlobalApply {
      fireItemChosen(taskTable.selectedItem ?: argumentTable.selectedItem)
    }

    row {
      scrollPane(taskTable).applyToComponent {
        preferredSize = JBUI.size(650, 180)
      }
    }
    row {
      scrollPane(argumentTable).applyToComponent {
        preferredSize = JBUI.size(650, 180)
      }
    }
  }

  init {
    title = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.title")
    setOKButtonText(IdeBundle.message("button.insert"))
    init()
  }

  private class Table(@NlsContexts.ColumnName name: String, items: List<Item>) : JBTable() {
    val selectedItem: Item?
      get() {
        if (selectedRow != -1) {
          @Suppress("UNCHECKED_CAST")
          val model = model as ListTableModel<Item>
          return model.getItem(selectedRow)
        }
        else {
          return null
        }
      }

    private fun installSpeedSearch() {
      val convertor = Convertor { it: Any? -> if (it is Item) it.name else "" }
      val search = object : TableSpeedSearch(this, convertor) {
        override fun selectElement(element: Any, selectedText: String) {
          super.selectElement(element, selectedText)
          repaint(visibleRect)
        }
      }
      search.comparator = SpeedSearchComparator(false, false)
    }

    private fun column(@NlsContexts.ColumnName name: String, valueOf: (Item) -> String) =
      object : ColumnInfo<Item, String>(name) {
        override fun valueOf(item: Item) = valueOf(item)
      }

    init {
      autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
      rowSelectionAllowed = true
      dragEnabled = false
      setShowGrid(false)
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      installSpeedSearch()
    }

    init {
      val taskColumnItem = column(name) { it.name }
      val descriptionColumnName = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.description.column")
      val argumentsColumnItem = column(descriptionColumnName) { it.description ?: "" }
      model = ListTableModel(arrayOf(taskColumnItem, argumentsColumnItem), items)
    }
  }

  data class Item(val name: String, val description: String?, val icon: Icon? = null)
}