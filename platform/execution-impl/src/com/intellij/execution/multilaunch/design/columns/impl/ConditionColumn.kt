package com.intellij.execution.multilaunch.design.columns.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ClientProperty
import com.intellij.ui.TableUtil
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.MultiLaunchConfigurationViewModel
import com.intellij.execution.multilaunch.design.columns.ExecutableTableColumn
import com.intellij.execution.multilaunch.design.components.DropDownDecorator
import com.intellij.execution.multilaunch.design.components.UnknownItemLabel
import com.intellij.execution.multilaunch.design.popups.SelectorPopupProvider
import com.intellij.execution.multilaunch.design.popups.SelectorPopupsContainer
import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.conditions.ConditionFactory
import com.intellij.execution.multilaunch.execution.conditions.ConditionTemplate
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class ConditionColumn(
  private val viewModel: MultiLaunchConfigurationViewModel
) : ExecutableTableColumn<Condition>(
  ExecutionBundle.message("run.configurations.multilaunch.table.column.condition")) {
  override fun setValue(item: ExecutableRow?, value: Condition?) {
    if (value == null) return
    item?.condition = value
  }

  override fun valueOf(item: ExecutableRow?) = item?.condition
  override fun getEditor(item: ExecutableRow?): ConditionColumnEditor? {
    return ConditionColumnEditor(viewModel, item ?: return null)
  }

  override fun getRenderer(item: ExecutableRow?) = ColumnRenderer(item)

  class ConditionColumnEditor(
    private val viewModel: MultiLaunchConfigurationViewModel,
    private val executableRow: ExecutableRow
  ) : AbstractTableCellEditor() {
    private var editorValue = ConditionOption(executableRow.condition)
    private val editorProperty = ConditionProperty(editorValue)
    override fun getCellEditorValue() = editorValue.condition

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component? {
      table ?: return null
      return EditorComponent(table, isSelected, true, executableRow)
    }

    private class ConditionOption(val condition: Condition?) {
      override fun equals(other: Any?): Boolean {
        return (other is ConditionOption && other.condition?.template?.type == condition?.template?.type)
      }

      override fun hashCode(): Int {
        return condition?.template?.type.hashCode()
      }
    }

    private class ConditionProperty(initial: ConditionOption) : MutableProperty<ConditionOption> {
      private var propertyValue: ConditionOption = initial
      override fun get() = propertyValue

      override fun set(value: ConditionOption) {
        propertyValue = value
      }
    }

    inner class EditorComponent(
      private val table: JTable,
      isSelected: Boolean,
      hasFocus: Boolean,
      private val executableRow: ExecutableRow
    ) : ColumnComponent(isSelected, hasFocus, executableRow), SelectorPopupsContainer, SelectorPopupProvider {
      private val popup by lazy { createPopup() }
      private val validity = AtomicProperty(true)

      override fun invokeSelectionPopup() {
        popup.showUnderneathOf(this)
      }

      override val selectorTarget = this
      override fun getSelectorPopupProviders() = listOf(this)

      private fun createPopupPanel(content: DialogPanel, actions: DialogPanel) =
        JBUI.Panels.simplePanel(JBUI.scale(10), JBUI.scale(10)).apply {
          addToCenter(content)
          addToBottom(actions)

          border = DialogWrapper.createDefaultBorder()
        }

      private fun createPopup(): JBPopup {
        val content = createContentPanel()
        val actions = createActionsPanel(table, content) { popup }
        val panel = createPopupPanel(content, actions)
        return JBPopupFactory.getInstance()
          .createComponentPopupBuilder(panel, null)
          .setMinSize(Dimension(JBUI.scale(350), JBUI.scale(20)))
          .setRequestFocus(true)
          .addListener(object : JBPopupListener {
            override fun beforeShown(event: LightweightWindowEvent) {
              val popup = event.asPopup()
              content.registerValidators(popup) {
                val isValid = it.isEmpty()
                validity.set(isValid)
              }
            }
          })
          .createPopup()
      }

      private fun createContentPanel() = panel {
        buttonsGroup(ExecutionBundle.message("run.configurations.multilaunch.condition.launch.when"), indent = true) {
          ConditionTemplate.EP_NAME.extensionList.forEach { template ->
            val rowCondition = executableRow.condition
            val condition = when {
              rowCondition != null && template.type == rowCondition.template.type -> rowCondition
              else -> ConditionFactory.getInstance(viewModel.project).create(template)
            }
            row {
              val button = radioButton(condition.text, ConditionOption(condition))
              condition.provideEditor(this)?.enabledIf(button.selected)
            }
          }
        }.bind(editorProperty)
      }

      private fun createActionsPanel(table: JTable, content: DialogPanel, popupProvider: () -> JBPopup?): DialogPanel {
        return panel {
          fun handleApply(e: ActionEvent) {
            content.apply()
            editorValue = editorProperty.get()
            TableUtil.stopEditing(table)
            popupProvider()?.closeOk(null)
          }

          row {
            button(ExecutionBundle.message("run.configurations.multilaunch.condition.apply"), ::handleApply)
              .align(AlignX.RIGHT)
              .applyToComponent {
                ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
              }
              .enabledIf(validity)
          }
        }
      }
    }
  }

  class ColumnRenderer(private val executableRow: ExecutableRow?) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean,row: Int, column: Int): Component {
      return when(executableRow) {
        null -> {
          text = ""
          icon = null
          this
        }
        else -> ColumnComponent(isSelected, hasFocus, executableRow)
      }
    }
  }

  open class ColumnComponent(
    isSelected: Boolean,
    hasFocus: Boolean,
    executableRow: ExecutableRow
  ) : DropDownDecorator() {
    init {
      border = JBUI.Borders.empty(5, 8, 5, 5)
      background = UIUtil.getTableBackground(isSelected, hasFocus)
      foreground = UIUtil.getTableForeground(isSelected, hasFocus)
      if (isSelected) setSelectionIcon()
      else setRegularIcon()
      when (executableRow.condition) {
        null -> setComponent(UnknownItemLabel("Condition was not found"))
        else -> setComponent(JLabel(executableRow.condition?.text))
      }
    }
  }
}
