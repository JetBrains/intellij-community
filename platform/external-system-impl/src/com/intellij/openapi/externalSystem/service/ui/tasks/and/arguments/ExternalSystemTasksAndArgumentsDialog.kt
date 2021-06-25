// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
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
  private val project: Project,
  private val externalSystemId: ProjectSystemId,
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
      ExternalSystemBundle.message("run.configuration.tasks.and.arguments.tasks.text"),
      AllIcons.General.Gear,
      items = tasksAndArguments.tasks.map { Item(it.name, it.description) }
    )
    val argumentTable = Table(
      ExternalSystemBundle.message("run.configuration.tasks.and.arguments.argument.column"),
      ExternalSystemBundle.message("run.configuration.tasks.and.arguments.arguments.text"),
      items = tasksAndArguments.arguments.map { Item(it.name, it.description) }
    )

    taskTable.clearSelectionWhenSelected(argumentTable)
    argumentTable.clearSelectionWhenSelected(taskTable)

    row { scrollPane(taskTable) }
    row { scrollPane(argumentTable) }

    onGlobalApply {
      fireItemChosen(taskTable.selectedItem ?: argumentTable.selectedItem)
    }
  }

  init {
    title = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.title")
    setOKButtonText(IdeBundle.message("button.insert"))
    init()
  }

  private inner class Table(
    @NlsContexts.ColumnName name: String,
    @NlsContexts.ColumnName contentName: String,
    icon: Icon? = null,
    items: List<Item>
  ) : JBTable() {
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

    private fun setEmptyText(@Nls text: String) {
      getAccessibleContext().accessibleName = text
      emptyText.text = text
    }

    init {
      val externalSystemName = externalSystemId.readableName
      setEmptyText(ExternalSystemBundle.message("initializing.0.projects.data", externalSystemName))

      val projectsManager = ExternalProjectsManager.getInstance(project)
      projectsManager.runWhenInitialized {
        setEmptyText(ExternalSystemBundle.message("run.configuration.tasks.and.arguments.no.content", externalSystemName, contentName))
      }
    }

    init {
      setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS)
      setRowSelectionAllowed(true)
      columnSelectionAllowed = false
      dragEnabled = false
      setShowGrid(false)
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }

    private fun column(@NlsContexts.ColumnName name: String, valueOf: (Item) -> String) =
      object : ColumnInfo<Item, String>(name) {
        override fun valueOf(item: Item) = valueOf(item)
      }

    init {
      val taskColumnInfo = column(name) { it.name }
      val descriptionColumnName = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.description.column")
      val argumentsColumnInfo = column(descriptionColumnName) { it.description ?: "" }
      model = ListTableModel(arrayOf(taskColumnInfo, argumentsColumnInfo), items)
    }

    init {
      val search = TableSpeedSearch(this)
      val nameColumn = columnModel.getColumn(0)
      nameColumn.cellRenderer = Renderer(search, icon)
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

  data class Item(@Nls val name: String, @Nls val description: String?)
}