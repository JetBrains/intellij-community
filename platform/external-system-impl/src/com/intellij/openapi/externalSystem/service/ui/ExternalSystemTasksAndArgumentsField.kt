// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui

import com.intellij.execution.ui.CommonParameterFragments
import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemTasksAndArgumentsField.TasksAndArgumentsInfo.Info
import com.intellij.openapi.externalSystem.service.ui.completetion.JTextCompletionContributor
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionContributor.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionPopup
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.comap
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.RecursionManager
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.layout.*
import com.intellij.ui.table.JBTable
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.table.IconTableCellRenderer
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

class ExternalSystemTasksAndArgumentsField(
  private val project: Project,
  private val tasksAndArgumentsInfo: TasksAndArgumentsInfo
) : ExtendableTextField() {

  private val propertyGraph = PropertyGraph()
  private val tasksAndArgumentsProperty = propertyGraph.graphProperty { "" }

  var tasksAndArguments by tasksAndArgumentsProperty

  init {
    bind(tasksAndArgumentsProperty.comap { it.trim() })
  }

  init {
    val message = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.empty.state")
    getAccessibleContext().accessibleName = message
    emptyText.text = message
    FragmentedSettingsUtil.setupPlaceholderVisibility(this)
    CommonParameterFragments.setMonospaced(this)
  }

  init {
    val action = Runnable { TasksAndArgumentsDialog().show() }
    val anAction = DumbAwareAction.create { action.run() }
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
    anAction.registerCustomShortcutSet(CustomShortcutSet(keyStroke), this, null)
    val keystrokeText = KeymapUtil.getKeystrokeText(keyStroke)
    val tooltip = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.tooltip") + " ($keystrokeText)"
    val browseExtension = ExtendableTextComponent.Extension.create(
      AllIcons.General.InlineVariables, AllIcons.General.InlineVariablesHover, tooltip, action)
    addExtension(browseExtension)
  }

  init {
    val textCompletionContributor = JTextCompletionContributor.create {
      tasksAndArgumentsInfo.tasks.map { TextCompletionInfo(it.name, it.description) } +
      tasksAndArgumentsInfo.arguments.mapNotNull { it.shortName?.let { n -> TextCompletionInfo(n, it.description) } } +
      tasksAndArgumentsInfo.arguments.map { TextCompletionInfo(it.name, it.description) }
    }
    val textCompletionPopup = TextCompletionPopup(project, this, textCompletionContributor)
    installJTextCompletionPopupTriggers(textCompletionPopup)
  }

  private inner class TasksAndArgumentsDialog : DialogWrapper(project) {

    override fun createCenterPanel() = panel {
      val taskTable = InfoTable(
        ExternalSystemBundle.message("run.configuration.tasks.and.arguments.task.column"),
        tasksAndArgumentsInfo.tasks
      )
      val argumentTable = InfoTable(
        ExternalSystemBundle.message("run.configuration.tasks.and.arguments.argument.column"),
        tasksAndArgumentsInfo.arguments
      )

      val taskColumn = taskTable.columnModel.getColumn(0)
      taskColumn.cellRenderer = IconTableCellRenderer.create(AllIcons.General.Gear)

      taskTable.clearSelectionWhenSelected(argumentTable)
      argumentTable.clearSelectionWhenSelected(taskTable)

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
      onGlobalApply(taskTable::apply)
      onGlobalApply(argumentTable::apply)
    }

    init {
      title = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.title")
      setOKButtonText(IdeBundle.message("button.insert"))
      init()
    }
  }

  private inner class InfoTable<I : Info>(
    @NlsContexts.ColumnName name: String,
    items: List<I>
  ) : JBTable(ListTableModel<I>(arrayOf(NameColumnInfo<I>(name), DescriptionColumnInfo<I>()), items)) {

    private val selectRecursionGuard =
      RecursionManager.createGuard<ExternalSystemTasksAndArgumentsField>(ExternalSystemTasksAndArgumentsField::class.java.name)

    fun clearSelectionWhenSelected(table: JTable) {
      table.selectionModel.addListSelectionListener {
        selectRecursionGuard.doPreventingRecursion(this@ExternalSystemTasksAndArgumentsField, false) {
          clearSelection()
        }
      }
    }

    fun apply() {
      if (selectedRow != -1) {
        val selectedItem = model.getItem(selectedRow)
        val separator = if (text.endsWith(" ")) "" else " "
        document.insertString(document.length, separator + selectedItem.name, null)
      }
    }

    private fun installSpeedSearch() {
      val convertor = Convertor { it: Any? -> if (it is Info) it.name else "" }
      val search = object : TableSpeedSearch(this, convertor) {
        override fun selectElement(element: Any, selectedText: String) {
          super.selectElement(element, selectedText)
          repaint(visibleRect)
        }
      }
      search.comparator = SpeedSearchComparator(false, false)
    }

    override fun getModel(): ListTableModel<I> {
      @Suppress("UNCHECKED_CAST")
      return super.getModel() as ListTableModel<I>
    }

    init {
      autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
      rowSelectionAllowed = true
      dragEnabled = false
      setShowGrid(false)
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      installSpeedSearch()
    }
  }

  private class NameColumnInfo<I : Info>(
    @NlsContexts.ColumnName nameColumnName: String
  ) : ColumnInfo<I, String>(nameColumnName) {
    override fun valueOf(item: I) = item.name
  }

  private class DescriptionColumnInfo<I : Info> : ColumnInfo<I, String>(
    ExternalSystemBundle.message("run.configuration.tasks.and.arguments.description.column")
  ) {
    override fun valueOf(item: I) = item.description
  }

  interface TasksAndArgumentsInfo {
    val tasks: List<TaskInfo>
    val arguments: List<ArgumentInfo>

    interface Info {
      val name: String
      val description: String?
    }

    data class TaskInfo(override val name: String, override val description: String?) : Info

    data class ArgumentInfo(override val name: String, val shortName: String?, override val description: String?) : Info
  }
}