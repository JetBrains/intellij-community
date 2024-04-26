package com.intellij.execution.multilaunch.design.columns.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.UIUtil
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.columns.ExecutableTableColumn
import com.intellij.execution.multilaunch.design.tooltips.TooltipProvider
import com.intellij.execution.multilaunch.design.tooltips.TooltipProvidersContainer
import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer

class DisableDebuggingColumn : ExecutableTableColumn<Boolean>("") {
  override fun setValue(item: ExecutableRow?, value: Boolean?) {
    if (value == null) return
    item?.disableDebugging = value
  }

  override fun valueOf(item: ExecutableRow?) = item?.disableDebugging
  override fun getRenderer(item: ExecutableRow?) = ColumnRenderer(item)
  override fun getEditor(item: ExecutableRow?) = ColumnEditor(item)
  override fun isCellEditable(item: ExecutableRow?) = item?.executable?.supportsDebugging ?: false

  override fun getTooltipText() = HtmlChunk.div()
    .child(HtmlChunk.text(
      ExecutionBundle.message("run.configurations.multilaunch.table.column.disable.debugging.title")).bold().wrapWith(HtmlChunk.div()))
    .child(HtmlChunk.br())
    .child(HtmlChunk.text(ExecutionBundle.message("run.configurations.multilaunch.table.column.disable.debugging.description")).wrapWith(HtmlChunk.div()))
    .toString()

  class ColumnRenderer(
    private val item: ExecutableRow?
  ) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
      return when (item?.executable?.supportsDebugging) {
        true -> ColumnContainer(ColumnComponent(item.disableDebugging == true, isSelected, hasFocus), isSelected, hasFocus)
        else -> JLabel()
      }
    }
  }

  class ColumnEditor(
    private val item: ExecutableRow?
  ) : AbstractTableCellEditor() {

    private var editorValue: Boolean? = null

    override fun getCellEditorValue() = editorValue

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
      return when (item?.executable?.supportsDebugging) {
        true -> ColumnContainer(EditorComponent(item.disableDebugging == true, isSelected, true), isSelected, true)
        else -> JLabel()
      }
    }

    inner class EditorComponent(
      value: Boolean,
      isSelected: Boolean,
      hasFocus: Boolean
    ) : ColumnComponent(value, isSelected, hasFocus) {
      init {
        addItemListener {
          when (it.stateChange) {
            ItemEvent.SELECTED -> editorValue = true
            ItemEvent.DESELECTED -> editorValue = false
          }
        }
      }
    }
  }

  class ColumnContainer(
    private val component: ColumnComponent,
    isSelected: Boolean,
    hasFocus: Boolean
  ) : JPanel(BorderLayout()), TooltipProvidersContainer {
    init {
      background = UIUtil.getTableBackground(isSelected, hasFocus)
      foreground = UIUtil.getTableForeground(isSelected, hasFocus)
      add(component, BorderLayout.CENTER)
    }

    override fun getTooltipProviders() = listOf(component)
  }

  open class ColumnComponent(
    value: Boolean,
    isSelected: Boolean,
    hasFocus: Boolean
  ) : JCheckBox("", value), TooltipProvider {
    init {
      horizontalAlignment = SwingConstants.CENTER
      background = UIUtil.getTableBackground(isSelected, hasFocus)
      foreground = UIUtil.getTableForeground(isSelected, hasFocus)
    }

    override val tooltipTarget = this
    override val tooltipText = HtmlChunk.div()
      .child(HtmlChunk.text(ExecutionBundle.message("run.configurations.multilaunch.table.row.disable.debugging.title")).bold().wrapWith(HtmlChunk.p()))
      .child(HtmlChunk.br())
      .child(HtmlChunk.text(ExecutionBundle.message("run.configurations.multilaunch.table.row.disable.debugging.description")).wrapWith(HtmlChunk.p()))
      .toString()
  }
}