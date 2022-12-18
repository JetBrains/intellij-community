// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.options.*
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.*
import javax.swing.*
import kotlin.math.max

class UiDslOptPaneRenderer : InspectionOptionPaneRenderer {
  override fun render(tool: InspectionProfileEntry): JComponent? {
    val pane = tool.optionsPane
    if (pane.components.isEmpty()) return null
    return render(tool, pane)
  }

  internal fun render(tool: InspectionProfileEntry, pane: OptPane): JComponent {
    return panel {
      pane.components.forEach { render(it, tool) }
    }
  }

  private fun Panel.render(component: OptComponent, tool: InspectionProfileEntry) {
    when (component) {
      is OptCheckbox -> {
        lateinit var checkbox: Cell<JBCheckBox>
        row {
          checkbox = checkBox(component.label.label())
            .applyToComponent {
              isSelected = tool.getOption(component.bindId) as Boolean
            }
            .onChanged { tool.setOption(component.bindId, it.isSelected) }
        }

        // Add checkbox nested components
        if (component.children.any()) {
          indent {
            component.children.forEach { render(it, tool) }
          }
            .enabledIf(checkbox.selected)
        }
      }

      is OptString -> {
        row {
          label(component.splitLabel.splitLabel().prefix)
            .gap(RightGap.SMALL)

          textField()
            .applyToComponent {
              if (component.width > 0) columns = component.width
              text = tool.getOption(component.bindId) as String
            }
            .onChanged { tool.setOption(component.bindId, it.text) }
            .gap(RightGap.SMALL)

          val suffix = component.splitLabel.splitLabel().suffix
          if (suffix.isNotBlank()) label(suffix)
        }
      }

      is OptNumber -> {
        row {
          label(component.splitLabel.splitLabel().prefix)
            .gap(RightGap.SMALL)

          cell(IntegerField().apply {
            minValue = component.minValue
            maxValue = component.maxValue
            columns = max(component.maxValue.toString().length, component.minValue.toString().length)
            value = tool.getOption(component.bindId) as Int
          })
            .onChanged { tool.setOption(component.bindId, it.value) }
            .gap(RightGap.SMALL)

          val suffix = component.splitLabel.splitLabel().suffix
          if (suffix.isNotBlank()) label(suffix)
        }
      }

      is OptDropdown -> {
        row {
          label(component.splitLabel.splitLabel().prefix)
            .gap(RightGap.SMALL)

          comboBox(getComboBoxModel(component.options), getComboBoxRenderer())
            .applyToComponent {
              val option = tool.getOption(component.bindId)
              @Suppress("HardCodedStringLiteral")
              model.selectedItem = if (option is Enum<*>) option.name else option.toString()
            }
            .onChanged { tool.setOption(component.bindId, convertItem((it.selectedItem as OptDropdown.Option).key, tool.getOption(component.bindId).javaClass)) }
            .gap(RightGap.SMALL)

          val suffix = component.splitLabel.splitLabel().suffix
          if (suffix.isNotBlank()) label(suffix)
        }
      }

      is OptCustom -> {
        row {
          val extension = CustomComponentExtension.find(component.componentId) ?: throw IllegalStateException(
            "Unregistered component: " + component.componentId)
          if (extension !is CustomComponentExtensionWithSwingRenderer<*>) {
            throw IllegalStateException("Component does not implement ")
          }
          // TODO: Get a parent somehow or update API
          cell(extension.render(component, JPanel()))
        }
      }

      is OptGroup -> {
        group(component.label.label()) {
          component.children.forEach { render(it, tool) }
        }
      }

      is OptSeparator -> separator()

      is OptTabSet -> {
        row {
          val tabbedPane = JBTabbedPane(SwingConstants.TOP)
          component.tabs.forEach { tab ->
            tabbedPane.add(tab.label.label(), com.intellij.ui.dsl.builder.panel {
              tab.content.forEach { tabComponent ->
                render(tabComponent, tool)
              }
            })
          }
          cell(tabbedPane)
            .align(Align.FILL)
            .resizableColumn()
        }
          .resizableRow()
      }

      is OptSettingLink -> {
        val label = HyperlinkLabel(component.displayName)
        label.addHyperlinkListener {
          val dataContext = DataManager.getInstance().getDataContext(label)

          val settings = Settings.KEY.getData(dataContext)
          if (settings == null) {
            val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return@addHyperlinkListener
            // Settings dialog was opened without configurable hierarchy tree in the left area
            // (e.g. by invoking "Edit inspection profile setting" fix)
            ShowSettingsUtil.getInstance().showSettingsDialog(
              project, { conf -> ConfigurableVisitor.getId(conf) == component.configurableID }
            ) { conf -> component.controlLabel?.let { label -> conf.focusOn(label) } }
          }
          else {
            val configurable = settings.find(component.configurableID) ?: return@addHyperlinkListener
            settings.select(configurable)
            component.controlLabel?.let { configurable.focusOn(it) }
          }
        }
        row { cell(label) }
      }

      is OptMap -> TODO()
      is OptSet -> TODO()
      is OptHorizontalStack -> TODO()
    }
  }

  private fun convertItem(key: String, type: Class<*>): Any {
    @Suppress("UNCHECKED_CAST")
    return when {
      type == Boolean::class.javaObjectType -> key.toBoolean()
      type == Int::class.javaObjectType -> key.toInt()
      type.isEnum -> java.lang.Enum.valueOf(type as Class<out Enum<*>>, key)
      type.superclass.isEnum -> java.lang.Enum.valueOf(type.superclass as Class<out Enum<*>>, key)
      else -> key 
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