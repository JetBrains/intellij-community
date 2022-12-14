// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.options.*
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.math.log10

class UiDslOptPaneRenderer : InspectionOptionPaneRenderer {
  override fun render(tool: InspectionProfileEntry,
                      pane: OptPane,
                      customControls: InspectionOptionPaneRenderer.CustomComponentProvider): JComponent {
    return panel {
      pane.components.forEach { render(it, tool, customControls) }
    }
  }

  private fun Panel.render(component: OptComponent,
                           tool: InspectionProfileEntry,
                           customControls: InspectionOptionPaneRenderer.CustomComponentProvider) {
    when (component) {
      is OptCheckbox -> {
        lateinit var checkbox: Cell<JBCheckBox>
        row {
          checkbox = checkBox(component.label.label())
            .applyToComponent {
              isSelected = tool.getOption(component.bindId) as Boolean
              addChangeListener { tool.setOption(component.bindId, isSelected) }
            }
        }

        // Add checkbox nested components
        if (component.children.any()) {
          indent {
            component.children.forEach { render(it, tool, customControls) }
          }
            .enabledIf(checkbox.selected)
        }
      }

      is OptString -> {
        row {
          label(component.splitLabel.splitLabel().prefix)

          textField()
            .applyToComponent {
              if (component.width > 0) columns = component.width
              text = tool.getOption(component.bindId) as String
              document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                  tool.setOption(component.bindId, text)
                }
              })
            }

          val suffix = component.splitLabel.splitLabel().suffix
          if (suffix.isNotBlank()) label(suffix)
        }
      }

      is OptNumber -> {
        row {
          label(component.splitLabel.splitLabel().prefix)

          cell(IntegerField().apply {
            minValue = component.minValue
            maxValue = component.maxValue
            columns = log10(component.maxValue.toDouble()).toInt() + 1
            value = tool.getOption(component.bindId) as Int
          })
            .applyToComponent {
              valueEditor.addListener { value -> tool.setOption(component.bindId, value) }
            }

          val suffix = component.splitLabel.splitLabel().suffix
          if (suffix.isNotBlank()) label(suffix)
        }
      }

      is OptDropdown -> {
        row {
          label(component.splitLabel.splitLabel().prefix)

          comboBox(getComboBoxModel(component.options), getComboBoxRenderer())
            .applyToComponent {
              model.selectedItem = tool.getOption(component.bindId)
              addItemListener { tool.setOption(component.bindId, (selectedItem as OptDropdown.Option).key) }
            }

          val suffix = component.splitLabel.splitLabel().suffix
          if (suffix.isNotBlank()) label(suffix)
        }
      }

      is OptCustom -> {
        row {
          // TODO: Get a parent somehow or update API
          cell(customControls.getCustomOptionComponent(component, JPanel()))
        }
      }

      is OptGroup -> {
        group(component.label.label()) {
          component.children.forEach { render(it, tool, customControls) }
        }
      }

      is OptSeparator -> separator()

      is OptMap -> TODO()
      is OptSet -> TODO()
      is OptHorizontalStack -> TODO()
      is OptTabSet -> TODO()
    }
  }

  private fun getComboBoxModel(items: MutableList<OptDropdown.Option>): ComboBoxModel<OptDropdown.Option> {
    return object : DefaultComboBoxModel<OptDropdown.Option>(items.toTypedArray()) {
      override fun setSelectedItem(anObject: Any?) {
        if (anObject is String) {
          val index = (0..size).firstOrNull { i -> getElementAt(i)?.key == anObject } ?: return
          super.setSelectedItem(getElementAt(index))
        }
        else {
          super.setSelectedItem(anObject)
        }
      }
    }
  }

  private fun getComboBoxRenderer(): ListCellRenderer<OptDropdown.Option?> {
    return object : SimpleListCellRenderer<OptDropdown.Option?>() {
      override fun customize(list: JList<out OptDropdown.Option?>,
                             value: OptDropdown.Option?,
                             index: Int,
                             selected: Boolean,
                             hasFocus: Boolean) {
        text = value?.label?.label()
      }
    }
  }
}