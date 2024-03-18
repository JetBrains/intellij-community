package com.intellij.execution.multilaunch.design.columns.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.runToolbar.components.TrimmedMiddleLabel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.MultiLaunchConfigurationViewModel
import com.intellij.execution.multilaunch.design.actions.AddExecutableAction
import com.intellij.execution.multilaunch.design.actions.ManageExecutableAction
import com.intellij.execution.multilaunch.design.actions.ReplaceExecutableAction
import com.intellij.execution.multilaunch.design.columns.ExecutableTableColumn
import com.intellij.execution.multilaunch.design.components.BadgeLabel
import com.intellij.execution.multilaunch.design.components.DropDownDecorator
import com.intellij.execution.multilaunch.design.components.UnknownItemLabel
import com.intellij.execution.multilaunch.design.popups.SelectorPopupProvider
import com.intellij.execution.multilaunch.design.popups.SelectorPopupsContainer
import com.intellij.execution.multilaunch.design.tooltips.TooltipProvider
import com.intellij.execution.multilaunch.design.tooltips.TooltipProvidersContainer
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.icons.AllIcons
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class ExecutableNameColumn(
  private val viewModel: MultiLaunchConfigurationViewModel
) : ExecutableTableColumn<ExecutableRow?>(
  ExecutionBundle.message("run.configurations.multilaunch.table.column.executable")) {
  override fun valueOf(item: ExecutableRow?): ExecutableRow? {
    return item
  }

  override fun getRenderer(item: ExecutableRow?): TableCellRenderer? {
    return when (item) {
      null -> AddExecutableColumn.ColumnRenderer(viewModel)
      else -> NameColumn.ColumnRenderer(viewModel, item)
    }
  }

  override fun getEditor(item: ExecutableRow?) = when (item) {
    null -> AddExecutableColumn.ColumnEditor(viewModel)
    else -> NameColumn.ColumnEditor(viewModel, item)
  }

  object AddExecutableColumn {
    class ColumnEditor(
      private val viewModel: MultiLaunchConfigurationViewModel
    ) : AbstractTableCellEditor() {
      override fun getCellEditorValue() = ""

      override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        return AddColumnComponent(viewModel)
      }
    }

    class ColumnRenderer(
      private val viewModel: MultiLaunchConfigurationViewModel
    ) : DefaultTableCellRenderer() {

      override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        return AddColumnComponent(viewModel)
      }
    }

    class AddColumnComponent(private val viewModel: MultiLaunchConfigurationViewModel) : ActionLink(
      ExecutionBundle.message("run.configurations.multilaunch.add.executable")) {
      init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = JBUI.Borders.empty(5, 8, 5, 5)
        background = UIUtil.getTableBackground(false, true)

        addActionListener(::handleAdd)
      }

      private fun handleAdd(e: ActionEvent) {
        val bounds = Rectangle(locationOnScreen.apply { translate(0, bounds.height) }, Dimension(bounds.width, bounds.height))
        val dataContext = ManageExecutableAction.createContext(viewModel.project, viewModel, null, bounds)
        val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.POPUP, Presentation.newTemplatePresentation(), dataContext)

        ActionManager.getInstance().getAction(AddExecutableAction.ID).actionPerformed(actionEvent)
      }
    }
  }

  object NameColumn {
    class ColumnEditor(
      private val viewModel: MultiLaunchConfigurationViewModel,
      private val executableRow: ExecutableRow
    ) : AbstractTableCellEditor() {
      override fun getCellEditorValue(): String {
        return ""
      }

      override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
        table ?: return null
        return NameColumnComponent(viewModel, table, row, column, executableRow)
      }
    }

    class ColumnRenderer(
      private val viewModel: MultiLaunchConfigurationViewModel,
      private val executableRow: ExecutableRow
    ) : DefaultTableCellRenderer() {
      override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component? {
        table ?: return null
        return NameColumnComponent(viewModel, table, row, column, executableRow)
      }
    }

    class NameColumnComponent(
      private val viewModel: MultiLaunchConfigurationViewModel,
      private val table: JTable,
      private val row: Int,
      private val column: Int,
      private val executableRow: ExecutableRow
    ) : DropDownDecorator(), TooltipProvidersContainer, SelectorPopupsContainer, SelectorPopupProvider {
      private val layout = object : BorderLayout() {
        override fun minimumLayoutSize(target: Container?): Dimension {
          // Required for trimmed name label to shrink properly
          return Dimension(0, 0)
        }
      }

      private val nameLabel: TooltipProvider =
        when (executableRow.executable) {
          null -> UnknownItemLabel("Task was not found")
          else -> NameLabel(executableRow.executable)
        }

      private val dragger = DraggerLabel().apply {
        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
      }
      private val namePanel = JPanel(layout).apply {
        isOpaque = false
        add(dragger, BorderLayout.WEST)
        add(nameLabel.tooltipTarget, BorderLayout.CENTER)
      }

      init {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = JBUI.Borders.empty(5, 5, 5, 5)
        background = UIUtil.getTableBackground(false, true)
        foreground = UIUtil.getTableForeground(false, true)
        setComponent(namePanel)
      }

      override fun getTooltipProviders(): List<TooltipProvider> =
        listOf(
          dragger,
          nameLabel
        )

      override val selectorTarget = this

      override fun invokeSelectionPopup() {
        val bounds = getSuggestedCellPopupBounds(table, row, column)
        val dataContext = ManageExecutableAction.createContext(viewModel.project, viewModel, executableRow, bounds)
        val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.POPUP, Presentation.newTemplatePresentation(), dataContext)
        ReplaceExecutableAction().actionPerformed(actionEvent)
      }

      override fun getSelectorPopupProviders() = listOf<SelectorPopupProvider>(this)
    }

    class DraggerLabel : JLabel(" ", AllIcons.General.Drag, SwingConstants.LEFT), TooltipProvider {
      override val tooltipTarget get() = this
      override val tooltipText get() = ExecutionBundle.message("run.configurations.multilaunch.dragger.tooltip")
    }

    class NameLabel(private val executable: Executable?) : TrimmedMiddleLabel(), TooltipProvider {
      init {
        icon = executable?.icon
        text = executable?.name
      }

      override val tooltipTarget get() = this
      override val tooltipText get() = executable?.name ?: ""
    }
  }
}
