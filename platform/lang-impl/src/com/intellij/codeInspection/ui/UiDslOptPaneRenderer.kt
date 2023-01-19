// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.options.*
import com.intellij.ide.DataManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.layout.selected
import com.intellij.util.applyIf
import java.awt.BorderLayout
import java.awt.EventQueue
import javax.swing.*
import kotlin.math.max


class UiDslOptPaneRenderer : InspectionOptionPaneRenderer {

  private data class RendererContext(val controller: OptionController, val project: Project?)

  override fun render(tool: InspectionProfileEntry,
                      pane: OptPane,
                      parent: Disposable?,
                      project: Project?): JComponent {
    return panel {
      pane.components.forEachIndexed { i, component ->
        render(component, RendererContext(tool.optionController, project), i != 0 && !pane.components[i - 1].hasBottomGap)
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
  private fun Panel.render(component: OptRegularComponent,
                           context: RendererContext,
                           isFirst: Boolean = false,
                           withBottomGap: Boolean = false) {
    when (component) {
      is OptControl, is OptTable, is OptSettingLink, is OptCustom -> {
        renderOptRow(component, context, withBottomGap)
      }

      is OptGroup -> {
        panel {
          row { label(component.label.label()) }
            .apply { if (isFirst) topGap(TopGap.SMALL) }
          indent {
            component.children.forEachIndexed { i, child -> render(child, context, i == 0, i == component.children.lastIndex) }
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
                render(tabComponent, context, i == 0)
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
            val cell = renderOptCell(child, context)
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
          component.children.forEachIndexed { i, child -> render(child, context, i == 0, i == component.children.lastIndex) }
        }
      }
    }
  }

  private fun Panel.renderOptRow(component: OptRegularComponent, context: RendererContext, withBottomGap: Boolean = false) {
    val splitLabel = component.splitLabel
    val nestedInRow = component.nestedInRow
    lateinit var cell: Cell<JComponent>
    when {
      // Split label
      splitLabel != null && splitLabel.suffix.isNotBlank() -> {
        row {
          label(splitLabel.prefix)
            .gap(RightGap.SMALL)
          cell = renderOptCell(component, context)
            .gap(RightGap.SMALL)
          label(splitLabel.suffix)
        }
      }
      // Split label with null suffix
      splitLabel?.prefix != null -> row(splitLabel.prefix) { cell = renderOptCell(component, context) }
      // No row label (align left, control handles the label)
      else -> row {
        cell = renderOptCell(component, context)

        nestedInRow?.let { nested ->
          renderOptCell(nested, context)
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
          .forEachIndexed { i, component -> render(component, context, i == 0) }
      }
      if (cell.component is JBCheckBox) {
        group.enabledIf((cell.component as JBCheckBox).selected)
      }
    }
  }

  private fun Row.renderOptCell(component: OptRegularComponent, context: RendererContext): Cell<JComponent> {
    return when (component) {
        is OptCheckbox -> {
          checkBox(component.label.label())
            .applyToComponent {
              isSelected = context.getOption(component.bindId) as Boolean
            }
            .onChanged { context.setOption(component.bindId, it.isSelected) }
            .apply { component.description?.let {
              gap(RightGap.SMALL)
              this@renderOptCell.contextHelp (HtmlBuilder().append(it).toString())
            } }
        }

        is OptString -> {
          textField()
            .applyToComponent {
              if (component.width > 0) columns = component.width
              text = context.getOption(component.bindId) as String? ?: ""
            }
            .validationOnInput { textField ->
              component.validator?.getErrorMessage(context.project, textField.text)?.let { error(it) }
            }
            .onChanged {
              context.setOption(component.bindId, it.text)
            }
            .apply { component.description?.let {
              gap(RightGap.SMALL)
              this@renderOptCell.contextHelp (HtmlBuilder().append(it).toString())
            } }
        }

        is OptNumber -> {
          val value = context.getOption(component.bindId)
          if (value is Double) {
            doubleTextField(component.minValue.toDouble(), component.maxValue.toDouble())
              .text(value.toString())
              .onChanged {
                try {
                  val number = it.text.toDouble()
                  context.setOption(component.bindId, number)
                } catch (_: NumberFormatException) {}
              }
          } else {
            intTextField(component.minValue..component.maxValue)
              .applyToComponent {
                columns = max(component.maxValue.toString().length, component.minValue.toString().length)
              }
              .text(value.toString())
              .onChanged {
                try {
                  val number = it.text.toInt()
                  context.setOption(component.bindId, number)
                } catch (_: NumberFormatException) {}
              }
          }
        }

        is OptDropdown -> {
          comboBox(getComboBoxModel(component.options), getComboBoxRenderer())
            .applyToComponent {
              val option = context.getOption(component.bindId)
              @Suppress("HardCodedStringLiteral")
              model.selectedItem = if (option is Enum<*>) option.name else option.toString()
            }
            .onChanged { context.setOption(component.bindId, convertItem(
              (it.selectedItem as OptDropdown.Option).key,
              context.getOption(component.bindId)?.javaClass ?: throw NullPointerException("OptDropdown value can't be initialized with 'null'.")
            )) }
            .gap(RightGap.SMALL)
        }

        is OptSettingLink -> {
          val label = HyperlinkLabel(component.displayName)
          label.addHyperlinkListener {
            val dataContext = DataManager.getInstance().getDataContext(label)

            val settings = Settings.KEY.getData(dataContext)
            if (settings == null) {
              val prj = context.project ?: CommonDataKeys.PROJECT.getData(dataContext) ?: return@addHyperlinkListener
              // Settings dialog was opened without configurable hierarchy tree in the left area
              // (e.g. by invoking "Edit inspection profile setting" fix)
              ShowSettingsUtil.getInstance().showSettingsDialog(
                prj, { conf -> ConfigurableVisitor.getId(conf) == component.configurableID }
              ) { conf -> component.controlLabel?.let { label -> conf.focusOn(label) } }
            }
            else {
              val configurable = settings.find(component.configurableID)
              if (configurable == null) {
                val hint = HintUtil.createInformationLabel(
                  SimpleColoredText(LangBundle.message("label.settings.page.not.found"), SimpleTextAttributes.ERROR_ATTRIBUTES))
                HintManager.getInstance().showHint(hint, RelativePoint.getNorthEastOf(label), HintManager.HIDE_BY_ANY_KEY, 5_000)
                return@addHyperlinkListener
              }
              settings.select(configurable)
              component.controlLabel?.let { configurable.focusOn(it) }
            }
          }
          cell(label)
        }

        is OptStringList -> {
          @Suppress("UNCHECKED_CAST") val list = context.getOption(component.bindId) as MutableList<String>
          val listWithListener = ListWithListener(list) { context.setOption(component.bindId, list) }
          val validator = component.validator
          val form = if (validator is StringValidatorWithSwingSelector) {
            ListEditForm("", component.label.label(), listWithListener, "", validator::select)
          } else {
            ListEditForm("", component.label.label(), listWithListener)
          }
          cell(form.contentPanel)
            .align(Align.FILL)
            .resizableColumn()
        }
        
        is OptTable -> {
          val columns = component.children.map { stringList ->
            @Suppress("UNCHECKED_CAST") val list = context.getOption(stringList.bindId) as MutableList<String>
            ListWithListener(list) { context.setOption(stringList.bindId, list) }
          }
          val columnNames = component.children.map { stringList -> stringList.label.label() }
          val table = ListTable(ListWrappingTableModel(columns, *columnNames.toTypedArray()))
          val panel = ToolbarDecorator.createDecorator(table)
            .setToolbarPosition(ActionToolbarPosition.LEFT)
            .setAddAction { _ ->
              val tableModel = table.model
              tableModel.addRow()
              EventQueue.invokeLater { editLastRow(table) }
            }
            .setRemoveAction { _ -> TableUtil.removeSelectedItems(table) }
            .disableUpDownActions().createPanel()
          val label = component.label.label()
          if (!label.isEmpty()) {
            panel.add(JLabel(label), BorderLayout.NORTH)
          }
          cell(panel)
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

        is OptCheckboxPanel, is OptGroup, is OptHorizontalStack, is OptSeparator, is OptTabSet -> { throw IllegalStateException("Unsupported nested component: ${component.javaClass}") }
    }
  }

  private fun RendererContext.getOption(bindId: String): Any? {
    return this.controller.getOption(bindId)
  }

  private fun RendererContext.setOption(bindId: String, value: Any) {
    this.controller.setOption(bindId, value)
  }

  private fun editLastRow(table: ListTable) {
    val tableModel = table.model
    val row = tableModel.rowCount - 1
    val selectionModel = table.selectionModel
    selectionModel.setSelectionInterval(row, row)
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(table, true) }
    val rectangle = table.getCellRect(row, 0, true)
    table.scrollRectToVisible(rectangle)
    table.editCellAt(row, 0)
    val editor = table.cellEditor
    val component = editor.getTableCellEditorComponent(table, tableModel.getValueAt(row, 0), true, row, 0)
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(component, true) }
  }
  
  private class ListWithListener(val list: MutableList<String>, val changeListener: () -> Unit): MutableList<String> by list {
    override fun removeAt(index: Int): String = list.removeAt(index).also { changeListener() }
    override fun remove(element: String): Boolean = list.remove(element).also { changeListener() }
    override fun add(element: String): Boolean = list.add(element).also { changeListener() }
    override fun set(index: Int, element: String): String = list.set(index, element).also { changeListener() }
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
    get() = this is OptStringList

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

  private fun Row.doubleTextField(min: Double, max: Double): Cell<JBTextField> {
    val result = cell(JBTextField())
      .validationOnInput {
        val value = it.text.toDoubleOrNull()
        when {
          value == null -> error(UIBundle.message("please.enter.a.number"))
          value < min || value > max -> error(UIBundle.message("please.enter.a.number.from.0.to.1", min, max))
          else -> null
        }
      }
    result.columns(10)
    return result
  }
}