// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.options.*
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.intellij.util.applyIf
import javax.swing.*
import kotlin.math.max

class UiDslOptPaneRenderer : InspectionOptionPaneRenderer {
  override fun render(tool: InspectionProfileEntry, pane: OptPane, parent: Disposable?): JComponent {
    return panel {
      pane.components.forEachIndexed { i, component ->
        render(component, tool.optionController, i != 0 && !pane.components[i - 1].hasBottomGap)
      }
    }
      .apply { if (parent != null) registerValidators(parent) }
  }

  /**
   * Renders an [OptComponent].
   *
   * @param isFirst true if the component is first in a hierarchy (then [OptGroup] doesn't need a top gap)
   * @param withBottomGap true if the component is the last of a group which should have a bottom gap
   */
  private fun Panel.render(component: OptRegularComponent, tool: OptionController, isFirst: Boolean = false, withBottomGap: Boolean = false) {
    when (component) {
      is OptControl, is OptSettingLink, is OptCustom -> {
        renderOptRow(component, tool, withBottomGap)
      }

      is OptGroup -> {
        panel {
          row { label(component.label.label()) }
            .apply { if (isFirst) topGap(TopGap.SMALL) }
          indent {
            component.children.forEachIndexed { i, child -> render(child, tool, i == 0, i == component.children.lastIndex) }
          }
        }
      }

      is OptSeparator -> separator()

      is OptTabSet -> {
        row {
          val tabbedPane = JBTabbedPane(SwingConstants.TOP)
          component.children.forEach { tab ->
            tabbedPane.add(tab.label.label(), com.intellij.ui.dsl.builder.panel {
              tab.children.forEachIndexed { i, tabComponent ->
                render(tabComponent, tool, i == 0)
              }
            })
          }
          cell(tabbedPane)
            .align(Align.FILL)
            .resizableColumn()
        }
          .resizableRow()
      }

      is OptHorizontalStack -> {
        row {
          component.children.forEach { child ->
            val splitLabel = child.splitLabel
            if (splitLabel != null && splitLabel.prefix.isNotBlank()) {
              label(splitLabel.prefix).gap(RightGap.SMALL)
            }
            val cell = renderOptCell(child, tool)
            if (splitLabel != null && splitLabel.suffix.isNotBlank()) {
              cell.gap(RightGap.SMALL)
              label(splitLabel.suffix)
            }
          }
        }
      }
      is OptCheckboxPanel -> {
        panel {
          // TODO: proper rendering
          component.children.forEachIndexed { i, child -> render(child, tool, i == 0, i == component.children.lastIndex) }
        }
      }
    }
  }

  private fun Panel.renderOptRow(component: OptRegularComponent, tool: OptionController, withBottomGap: Boolean = false) {
    val splitLabel = component.splitLabel
    val nestedInRow = component.nestedInRow
    lateinit var cell: Cell<JComponent>
    when {
      // Split label
      splitLabel != null && splitLabel.suffix.isNotBlank() -> {
        row {
          label(splitLabel.prefix)
            .gap(RightGap.SMALL)
          cell = renderOptCell(component, tool)
            .gap(RightGap.SMALL)
          label(splitLabel.suffix)
        }
      }
      // Split label with null suffix
      splitLabel?.prefix != null -> row(splitLabel.prefix) { cell = renderOptCell(component, tool) }
      // No row label (align left, control handles the label)
      else -> row {
        cell = renderOptCell(component, tool)

        nestedInRow?.let { nested ->
          renderOptCell(nested, tool)
            .enabledIf((cell.component as JBCheckBox).selected)
        }
      }
    }
      .applyIf(withBottomGap) { bottomGap(BottomGap.SMALL) }
      .applyIf(component.hasResizableRow) { resizableRow() }

    // Nested components
    component.nestedControls?.let { nested ->
      val group = indent {
        nested
          .drop(if (nestedInRow != null) 1 else 0)
          .forEachIndexed { i, component -> render(component, tool, i == 0) }
      }
      if (cell.component is JBCheckBox) {
        group.enabledIf((cell.component as JBCheckBox).selected)
      }
    }
  }

  private fun Row.renderOptCell(component: OptRegularComponent, tool: OptionController): Cell<JComponent> {
    return when (component) {
        is OptCheckbox -> {
          checkBox(component.label.label())
            .applyToComponent {
              isSelected = tool.getOption(component.bindId) as Boolean
            }
            .onChanged { tool.setOption(component.bindId, it.isSelected) }
            .apply { component.description?.let {
              gap(RightGap.SMALL)
              this@renderOptCell.contextHelp (HtmlBuilder().append(it).toString())
            } }
        }

        is OptString -> {
          textField()
            .applyToComponent {
              if (component.width > 0) columns = component.width
              text = tool.getOption(component.bindId) as String
            }
            .validationOnInput { textField ->
              component.validator?.getErrorMessage(textField.text)?.let { error(it) }
            }
            .onChanged {
              tool.setOption(component.bindId, it.text)
            }
        }

        is OptNumber -> {
          intTextField(component.minValue..component.maxValue)
            .applyToComponent {
              columns = max(component.maxValue.toString().length, component.minValue.toString().length)
            }
            .text(tool.getOption(component.bindId).toString())
            .onChanged {
              try {
                val number = it.text.toInt()
                tool.setOption(component.bindId, number)
              } catch (_: NumberFormatException) {}
            }
        }

        is OptDropdown -> {
          comboBox(getComboBoxModel(component.options), getComboBoxRenderer())
            .applyToComponent {
              val option = tool.getOption(component.bindId)
              @Suppress("HardCodedStringLiteral")
              model.selectedItem = if (option is Enum<*>) option.name else option.toString()
            }
            .onChanged { tool.setOption(component.bindId, convertItem((it.selectedItem as OptDropdown.Option).key, tool.getOption(component.bindId).javaClass)) }
            .gap(RightGap.SMALL)
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
          cell(label)
        }

        is OptSet -> {
          @Suppress("UNCHECKED_CAST") val list = tool.getOption(component.bindId) as MutableList<String>
          val validator = component.validator
          val form = if (validator is StringValidatorWithSwingSelector) {
            ListEditForm("", component.label.label(), list, "", validator::select)
          } else {
            ListEditForm("", component.label.label(), list)
          }
          cell(form.contentPanel)
            .align(Align.FILL)
            .resizableColumn()
        }

        is OptCustom -> {
          val extension = CustomComponentExtension.find(component.componentId) ?: throw IllegalStateException(
            "Unregistered component: " + component.componentId)
          if (extension !is CustomComponentExtensionWithSwingRenderer<*>) {
            throw IllegalStateException("Component does not implement ")
          }
          // TODO: Get a parent somehow or update API
          cell(extension.render(component, JPanel()))
        }

        is OptMap -> TODO()

        is OptCheckboxPanel, is OptGroup, is OptHorizontalStack, is OptSeparator, is OptTabSet -> { throw IllegalStateException("Unsupported nested component: ${component.javaClass}") }
    }
  }

  private val OptComponent.splitLabel: LocMessage.PrefixSuffix?
    get() = when (this) {
      is OptString -> splitLabel.splitLabel()
      is OptNumber -> splitLabel.splitLabel()
      is OptDropdown -> splitLabel.splitLabel()
      else -> null
    }

  private val OptComponent.nestedControls: List<OptRegularComponent>?
    get() = when(this) {
      is OptCheckbox -> children
      else -> null
    }

  /**
   * Returns the nested component to be rendered in the parent row.
   * This is the case for [OptString], [OptNumber] or [OptDropdown] with blank labels as first checkbox child.
   */
  private val OptComponent.nestedInRow: OptRegularComponent?
    get() {
      if (this !is OptCheckbox) return null
      val child = children.firstOrNull() ?: return null
      val label = child.splitLabel ?: return null
      if (label.prefix.isBlank() && label.suffix.isBlank()) return child
      return null
    }


  private val OptComponent.hasBottomGap: Boolean
    get() = this is OptGroup

  private val OptComponent.hasResizableRow: Boolean
    get() = this is OptSet

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