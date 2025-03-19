// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.ide.util.scopeChooser.EditScopesDialog
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable.PROJECT_SCOPES
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.keymap.KeymapUtil.getShortcutsText
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsSafe
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.ui.ColorChooserService
import com.intellij.ui.ColorUtil.toHex
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.FileColorManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.ToolbarDecorator.createDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.layout.selected
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.EditableModel
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.RegionPaintIcon
import com.intellij.util.ui.RegionPainter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.awt.*
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

private const val ID = "reference.settings.ide.settings.file-colors"
@PropertyKey(resourceBundle = "messages.IdeBundle") private const val DISPLAY_NAME_KEY = "configurable.file.colors"

internal class FileColorsConfigurable(private val project: Project) : BoundSearchableConfigurable(message(DISPLAY_NAME_KEY), ID), NoScroll {

  private val colorsTableModel = FileColorsTableModel(FileColorManager.getInstance(project) as FileColorManagerImpl)

  override fun createPanel(): DialogPanel {
    val manager = FileColorManager.getInstance(project) as FileColorManagerImpl
    lateinit var result: DialogPanel
    result = panel {
      row {
        val cbEnabledFileColors = checkBox(message("settings.file.colors.enable.file.colors"))
          .bindSelected(manager::isEnabled, manager::setEnabled)
          .component
        checkBox(message("settings.file.colors.use.in.editor.tabs"))
          .bindSelected(manager::isEnabledForTabs) { FileColorManagerImpl.setEnabledForTabs(it) }
          .enabledIf(cbEnabledFileColors.selected)
        checkBox(message("settings.file.colors.use.in.project.view"))
          .bindSelected(manager::isEnabledForProjectView) { FileColorManagerImpl.setEnabledForProjectView(it) }
          .enabledIf(cbEnabledFileColors.selected)
      }
      row {
        cell(colorsTableModel.createComponent())
          .align(Align.FILL)
          .comment(message("settings.file.colors.description"))
          .onIsModified { colorsTableModel.isModified }
          .onReset { colorsTableModel.reset() }
          .onApply { colorsTableModel.apply() }
      }.resizableRow()
      row {
        link(message("settings.file.colors.manage.scopes")) {
          Settings.KEY.getData(DataManager.getInstance().getDataContext(result))?.let {
            try {
              // try to select related configurable in the current Settings dialog
              if (!it.select(it.find(PROJECT_SCOPES)).isRejected) return@link
            }
            catch (ignored: IllegalStateException) {
              // see ScopeColorsPageFactory.java:74
            }
          }
          EditScopesDialog.showDialog(manager.project, null, true)
        }
      }
    }
    return result
  }

  override fun apply() {
    super.apply()
    UISettings.getInstance().fireUISettingsChanged()
    ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
  }
}

// table support

private class Column(private val key: String, val type: Class<*>, val editable: Boolean) {
  val name: String
    get() = message(key)
}

private val columns = arrayOf(
  Column("settings.file.color.column.scope", String::class.java, false),
  Column("settings.file.color.column.color", FileColorConfiguration::class.java, true),
  Column("settings.file.color.column.shared", Boolean::class.javaObjectType, true))

private class FileColorsTableModel(val manager: FileColorManagerImpl) : AbstractTableModel(), EditableModel, UnnamedConfigurable {
  private val local = mutableListOf<FileColorConfiguration>()
  private val shared = mutableListOf<FileColorConfiguration>()
  private var table: JTable? = null

  private fun copy(list: List<FileColorConfiguration>) = list.map { copy(it) }
  private fun copy(configuration: FileColorConfiguration) = FileColorConfiguration(configuration.scopeName, configuration.colorID)

  private fun selectRow(row: Int) {
    val table = table ?: return
    table.setRowSelectionInterval(row, row)
    table.scrollRectToVisible(table.getCellRect(row, 0, true))
  }

  private fun getConfiguration(row: Int): FileColorConfiguration? {
    val index = getSharedIndex(row) ?: return null
    return if (index < 0) local[row] else shared[index]
  }

  private fun getSharedIndex(row: Int): Int? {
    if (row < 0) return null
    val index = row - local.size
    return if (index < shared.size) index else null
  }

  private fun resolveCustomColor(value: Any?): String? {
    val name = value as? String ?: return null
    if (null != manager.getColor(name)) return name
    val parent = table ?: return null
    return ColorChooserService.instance.showDialog(null, parent, message("settings.file.colors.dialog.choose.color"), null)?.let { toHex(it) }
  }

  private fun resolveDuplicate(scopeName: String, colorName: String, toSharedList: Boolean): Boolean {
    val list = if (toSharedList) shared else local
    val index = list.indexOfFirst { it.scopeName == scopeName }
    if (index < 0) return false
    val parent = table ?: return false
    val presentableName = findScope(scopeName, manager.project)!!.presentableName
    val title = when (toSharedList) {
      true -> message("settings.file.colors.dialog.warning.shared", presentableName)
      else -> message("settings.file.colors.dialog.warning.local", presentableName)
    }
    val configuration = list[index]
    val update = when (configuration.colorID == colorName) {
      true -> {
        Messages.YES != Messages.showYesNoDialog(
          parent,
          message("settings.file.colors.dialog.warning.append"),
          title,
          Messages.getWarningIcon())
      }
      else -> {
        val oldColor = manager.getColor(configuration.colorID)?.let { toHex(it) } ?: ""
        val newColor = manager.getColor(colorName)?.let { toHex(it) } ?: ""
        Messages.OK == Messages.showOkCancelDialog(
          parent,
          message("settings.file.colors.dialog.warning.replace",
                  oldColor,
                  newColor),
          title,
          message("settings.file.colors.dialog.warning.update"),
          Messages.getCancelButton(),
          Messages.getWarningIcon())
      }
    }
    if (!update) return false
    configuration.colorID = colorName
    val row = if (toSharedList) local.size + index else index
    fireTableRowsUpdated(row, row)
    selectRow(row)
    return true
  }

  private fun onRowInserted(row: Int) {
    fireTableRowsInserted(row, row)
    selectRow(row)
  }

  fun addScopeColor(scope: NamedScope, color: String?) {
    val colorName = resolveCustomColor(color) ?: return
    if (resolveDuplicate(scope.scopeId, colorName, false)) return
    local.add(0, FileColorConfiguration(scope.scopeId, colorName))
    onRowInserted(0)
  }

  fun getColors(): List<@Nls String> {
    val list = mutableListOf<String>()
    list += manager.colorNames
    list += message("settings.file.color.custom.name")
    return list
  }

  // TableModel

  override fun getColumnCount() = columns.size

  override fun getColumnName(column: Int) = columns[column].name

  override fun getColumnClass(column: Int) = columns[column].type

  override fun isCellEditable(row: Int, column: Int) = columns[column].editable

  override fun getRowCount() = local.size + shared.size

  override fun getValueAt(row: Int, column: Int): Any? {
    return when (column) {
      0 -> getConfiguration(row)?.scopeName
      1 -> getConfiguration(row)
      2 -> row >= local.size
      else -> null
    }
  }

  override fun setValueAt(value: Any?, row: Int, column: Int) {
    when (column) {
      1 -> {
        val configuration = getConfiguration(row) ?: return
        configuration.colorID = resolveCustomColor(value) ?: return
        fireTableCellUpdated(row, column)
      }
      2 -> {
        val index = getSharedIndex(row) ?: return
        if (index < 0) {
          val configuration = local.removeAt(row)
          fireTableRowsDeleted(row, row)
          if (resolveDuplicate(configuration.scopeName, configuration.colorID, true)) return
          shared.add(0, configuration)
          onRowInserted(local.size)
        }
        else if (index < shared.size) {
          val configuration = shared.removeAt(index)
          fireTableRowsDeleted(row, row)
          if (resolveDuplicate(configuration.scopeName, configuration.colorID, false)) return
          local.add(configuration)
          onRowInserted(local.size - 1)
        }
      }
    }
  }

  // EditableModel

  override fun addRow() = throw UnsupportedOperationException()

  override fun removeRow(row: Int) {
    val index = getSharedIndex(row) ?: return
    if (index < 0) local.removeAt(row) else shared.removeAt(index)
    fireTableRowsDeleted(row, row)
  }

  override fun exchangeRows(oldRow: Int, newRow: Int) {
    if (oldRow == newRow) return
    val oldIndex = getSharedIndex(oldRow) ?: return
    val newIndex = getSharedIndex(newRow) ?: return
    when {
      (oldIndex < 0) && (newIndex < 0) -> exchangeRows(local, oldRow, newRow)
      oldIndex < 0 || newIndex < 0 -> return // cannot move from local to shared and vice versa
      else -> exchangeRows(shared, oldIndex, newIndex)
    }
    fireTableRowsUpdated(oldRow, oldRow)
    fireTableRowsUpdated(newRow, newRow)
  }

  private fun exchangeRows(list: MutableList<FileColorConfiguration>, oldIndex: Int, newIndex: Int) {
    val maxIndex = oldIndex.coerceAtLeast(newIndex)
    val minIndex = oldIndex.coerceAtMost(newIndex)
    val maxConfiguration = list.removeAt(maxIndex)
    val minConfiguration = list.removeAt(minIndex)
    list.add(minIndex, maxConfiguration)
    list.add(maxIndex, minConfiguration)
  }

  override fun canExchangeRows(oldRow: Int, newRow: Int): Boolean {
    if (oldRow == newRow) return true
    val oldIndex = getSharedIndex(oldRow) ?: return false
    val newIndex = getSharedIndex(newRow) ?: return false
    return (oldIndex < 0) == (newIndex < 0)
  }

  // UnnamedConfigurable

  override fun createComponent(): JComponent {
    val table = JBTable(this)
    table.setShowGrid(false)
    TableHoverListener.DEFAULT.removeFrom(table)
    table.emptyText.text = message("settings.file.colors.no.colors.specified")

    table.emptyText.appendSecondaryText(message("settings.file.colors.add.colors.link"), LINK_PLAIN_ATTRIBUTES) {
      val popup = JBPopupFactory.getInstance().createListPopup(ScopeListPopupStep(this))
      popup.showInCenterOf(table)
    }
    val shortcut = getShortcutsText(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD).shortcuts)
    if (shortcut.isNotEmpty()) table.emptyText.appendText(" ($shortcut)")

    this.table = table

    table.tableHeader.defaultRenderer = TableHeaderRenderer()
    table.setDefaultRenderer(String::class.java, TableScopeRenderer(manager))
    // configure color renderer and its editor
    val editor: ComboBox<String> = ComboBox(getColors().toTypedArray())
    editor.renderer = ComboBoxColorRenderer(manager)
    table.setDefaultEditor(FileColorConfiguration::class.java, DefaultCellEditor(editor))
    table.setDefaultRenderer(FileColorConfiguration::class.java, TableColorRenderer(manager))
    // align boolean renderer to left
    val booleanRenderer = table.getDefaultRenderer(Boolean::class.javaObjectType)
    val rendererCheckBox = booleanRenderer as? JCheckBox
    rendererCheckBox?.horizontalAlignment = SwingConstants.LEFT
    // align boolean editor to left
    val booleanEditor = table.getDefaultEditor(Boolean::class.javaObjectType)
    val editorWrapper = booleanEditor as? DefaultCellEditor
    val editorCheckBox = editorWrapper?.component as? JCheckBox
    editorCheckBox?.horizontalAlignment = SwingConstants.LEFT
    // create and configure table decorator
    return createDecorator(table)
      .setAddAction {
        val popup = JBPopupFactory.getInstance().createListPopup(ScopeListPopupStep(this))
        it.preferredPopupPoint.let { point -> popup.show(point) }
      }
      .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN)
      .setMoveUpActionUpdater { table.selectedRows.all { canExchangeRows(it, it - 1) } }
      .setMoveDownActionUpdater { table.selectedRows.all { canExchangeRows(it, it + 1) } }
      .createPanel()
  }

  override fun isModified(): Boolean {
    return local != manager.applicationLevelConfigurations || shared != manager.projectLevelConfigurations
  }

  override fun apply() {
    manager.model.setConfigurations(copy(local), false)
    manager.model.setConfigurations(copy(shared), true)
  }

  override fun reset() {
    local.clear()
    local.addAll(copy(manager.applicationLevelConfigurations))
    shared.clear()
    shared.addAll(copy(manager.projectLevelConfigurations))
    fireTableDataChanged()
  }
}

// renderers

private class ColorPainter(val color: Color) : RegionPainter<Component?> {
  fun asIcon(): Icon = RegionPaintIcon(36, 12, this).withIconPreScaled(false)

  override fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, c: Component?) {
    g.color = color
    g.fillRect(x, y, width, height)
  }
}

private fun updateColorRenderer(renderer: JLabel, selected: Boolean, background: Color?): JLabel {
  if (!selected) renderer.background = background
  renderer.horizontalTextPosition = SwingConstants.LEFT
  renderer.icon = background?.let { if (selected) ColorPainter(it).asIcon() else null }
  return renderer
}

private class ComboBoxColorRenderer(val manager: FileColorManagerImpl) : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focused: Boolean): Component {
    super.getListCellRendererComponent(list, value, index, selected, focused)
    return updateColorRenderer(this, selected, value?.toString()?.let { manager.getColor(it) })
  }
}

private class TableColorRenderer(val manager: FileColorManagerImpl) : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable?, value: Any?,
                                             selected: Boolean, focused: Boolean, row: Int, column: Int): Component {
    val configuration = value as? FileColorConfiguration
    super.getTableCellRendererComponent(table, configuration?.colorID?.let { manager.getColorName(it) }, selected, focused, row, column)
    return updateColorRenderer(this, selected, configuration?.colorID?.let { manager.getColor(it) })
  }

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
    val bounds = Rectangle(width, height)
    JBInsets.removeFrom(bounds, insets)
    val icon = AllIcons.General.ArrowDown
    icon.paintIcon(this, g,
                   bounds.x + bounds.width - icon.iconWidth,
                   bounds.y + (bounds.height - icon.iconHeight) / 2)
  }
}

private class TableHeaderRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable?, value: Any?,
                                             selected: Boolean, focused: Boolean, row: Int, column: Int): Component {
    val component = super.getTableCellRendererComponent(table, value, selected, focused, row, column)
    horizontalTextPosition = SwingConstants.LEFT
    toolTipText = if (column == 2) message("settings.file.color.column.shared.help") else null
    icon = if (column == 2) AllIcons.General.ContextHelp else null
    return component
  }
}

private class TableScopeRenderer(val manager: FileColorManagerImpl) : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable?, value: Any?,
                                             selected: Boolean, focused: Boolean, row: Int, column: Int): Component {
    val component = super.getTableCellRendererComponent(table, value, selected, focused, row, column)
    @NlsSafe val name = value?.toString()
    val namedScope = name?.let { findScope(it, manager.project) }
    toolTipText = if (namedScope == null) message("settings.file.colors.scope.unknown") else null
    icon = if (namedScope == null) AllIcons.General.Error else null
    text = if (namedScope != null) namedScope.presentableName else name
    return component
  }
}

private fun getScopes(project: Project): List<NamedScope> {
  val list = mutableListOf<NamedScope>()
  list += DependencyValidationManager.getInstance(project).scopes
  list += NamedScopeManager.getInstance(project).scopes
  return list.filter { it.value != null }
}

private fun findScope(scopeName: String, project: Project) = NamedScopesHolder.getScope(project, scopeName)

// popup steps

private class ScopeListPopupStep(val model: FileColorsTableModel)
  : BaseListPopupStep<NamedScope>(null, getScopes(model.manager.project)) {
  override fun getTextFor(scope: NamedScope?) = scope?.presentableName ?: ""
  override fun getIconFor(scope: NamedScope?) = scope?.icon
  override fun hasSubstep(selectedValue: NamedScope?) = true
  override fun onChosen(scope: NamedScope?, finalChoice: Boolean): PopupStep<*>? {
    return scope?.let { ColorListPopupStep(model, it) }
  }
}

private class ColorListPopupStep(val model: FileColorsTableModel, val scope: NamedScope)
  : BaseListPopupStep<String>(null, model.getColors()) {
  override fun getBackgroundFor(value: String?) = value?.let { model.manager.getColor(it) }
  override fun onChosen(value: String?, finalChoice: Boolean): PopupStep<*>? {
    // invoke later to close popup before showing dialog
    invokeLater { model.addScopeColor(scope, value) }
    return null
  }
}

@ApiStatus.Internal
class FileColorsSearchOptionContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    val displayName = message(DISPLAY_NAME_KEY)
    processor.addOptions("Folder Colors", null, displayName, ID, displayName, false)
    processor.addOptions("Directory Colors", null, displayName, ID, displayName, false)
  }
}