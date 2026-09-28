// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.propertiesComponent

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.util.BasePropertyService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.PlatformInternalBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SideBorder
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.DefaultCellEditor
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.RowSorter
import javax.swing.SortOrder
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

private const val KEY_COLUMN = 0
private const val VALUE_COLUMN = 1
private const val TYPE_COLUMN = 2

/**
 * Shows the [PropertiesComponent] values of the application and of the [project] in separate tabs.
 *
 * The dialog writes each change to the [PropertiesComponent] when the edit is complete.
 */
internal class PropertiesComponentDialog(private val project: Project?) : DialogWrapper(project, true) {
  private val panels = ArrayList<PropertiesComponentPanel>()

  init {
    title = PlatformInternalBundle.message("dialog.title.properties.component")
    setOKButtonText(CommonBundle.getCloseButtonText())
    init()
  }

  override fun createCenterPanel(): JComponent {
    val tabs = JBTabbedPane()
    addTab(tabs, PlatformInternalBundle.message("properties.component.tab.application"), PropertiesComponent.getInstance())
    if (project != null && !project.isDefault) {
      addTab(tabs, PlatformInternalBundle.message("properties.component.tab.project"), PropertiesComponent.getInstance(project))
    }
    tabs.preferredSize = JBUI.DialogSizes.extraLarge()
    return tabs
  }

  private fun addTab(tabs: JBTabbedPane, title: @NlsContexts.TabTitle String, properties: PropertiesComponent) {
    val panel = PropertiesComponentPanel(properties)
    panels.add(panel)
    tabs.addTab(title, panel.content)
  }

  override fun createActions(): Array<Action> = arrayOf(okAction)

  override fun getPreferredFocusedComponent(): JComponent? = panels.firstOrNull()?.table

  override fun getDimensionServiceKey(): String = "PropertiesComponentDialog"

  override fun doOKAction() {
    panels.forEach { it.table.cellEditor?.stopCellEditing() }
    super.doOKAction()
  }

  override fun doCancelAction() {
    panels.forEach { it.table.cellEditor?.cancelCellEditing() }
    super.doCancelAction()
  }
}

private class PropertiesComponentPanel(properties: PropertiesComponent) {
  private val model = PropertiesTableModel(properties) { updateDetails() }
  val table = JBTable(model)
  private val speedSearch: TableSpeedSearch
  private val details = JTextArea(4, 50)
  val content = JPanel(BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP))

  init {
    table.setShowGrid(false)
    table.tableHeader.reorderingAllowed = false
    table.putClientProperty("terminateEditOnFocusLost", true)
    if (!model.isSupported) {
      table.emptyText.text = PlatformInternalBundle.message("properties.component.unsupported")
    }

    val renderer = TextRenderer()
    with(table.columnModel.getColumn(KEY_COLUMN)) {
      preferredWidth = JBUI.scale(350)
      cellRenderer = renderer
    }
    with(table.columnModel.getColumn(VALUE_COLUMN)) {
      preferredWidth = JBUI.scale(450)
      cellRenderer = renderer
      cellEditor = DefaultCellEditor(JBTextField().apply { border = JBUI.Borders.empty() })
    }
    table.columnModel.getColumn(TYPE_COLUMN).preferredWidth = JBUI.scale(60)

    speedSearch = TableSpeedSearch.installOn(table) { value, cell -> if (cell.column == TYPE_COLUMN) "" else value?.toString() ?: "" }
    speedSearch.setFilteringMode(true)

    val sorter = TableRowSorter(model)
    sorter.setComparator(KEY_COLUMN, String.CASE_INSENSITIVE_ORDER)
    sorter.sortKeys = listOf(RowSorter.SortKey(KEY_COLUMN, SortOrder.ASCENDING))
    table.rowSorter = sorter

    table.selectionModel.addListSelectionListener { e ->
      if (!e.valueIsAdjusting) updateDetails()
    }
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val viewRow = table.rowAtPoint(event.point)
        if (viewRow < 0) return false
        val modelRow = table.convertRowIndexToModel(viewRow)
        // the table starts the inline editor itself on a double click in an editable cell
        if (table.columnAtPoint(event.point) == VALUE_COLUMN && model.isCellEditable(modelRow, VALUE_COLUMN)) return false
        edit(viewRow)
        return true
      }
    }.installOn(table)

    details.margin = JBUI.insets(2)
    details.lineWrap = true
    details.wrapStyleWord = true
    details.isEditable = false
    details.background = UIUtil.getPanelBackground()
    details.font = JBFont.label()

    val group = DefaultActionGroup(EditAction(), RemoveAction(), RefreshAction())
    val toolbar = ActionManager.getInstance().createActionToolbar("PropertiesComponent", group, true)
    toolbar.targetComponent = table

    content.add(toolbar.component, BorderLayout.NORTH)
    content.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
    content.add(ScrollPaneFactory.createScrollPane(details, SideBorder.NONE), BorderLayout.SOUTH)

    model.reload()
    ScrollingUtil.ensureSelectionExists(table)
  }

  private fun canChangeSelection(): Boolean = !table.isEditing && !speedSearch.isPopupActive

  private fun edit(viewRow: Int) {
    val modelRow = table.convertRowIndexToModel(viewRow)
    if (model.isCellEditable(modelRow, VALUE_COLUMN)) {
      if (table.editCellAt(viewRow, VALUE_COLUMN)) {
        IdeFocusManager.getGlobalInstance().requestFocus(table.editorComponent ?: return, true)
      }
      return
    }

    val entry = model.getEntry(modelRow)
    val dialog = MultilineValueDialog(table, entry)
    if (dialog.showAndGet()) {
      model.setText(modelRow, dialog.text)
    }
  }

  private fun removeSelected() {
    val viewRows = table.selectedRows
    if (viewRows.isEmpty()) return
    val message = PlatformInternalBundle.message("properties.component.remove.message", viewRows.size, model.getEntry(table.convertRowIndexToModel(viewRows[0])).key)
    if (Messages.showYesNoDialog(table, message, PlatformInternalBundle.message("properties.component.remove.title"), Messages.getQuestionIcon()) != Messages.YES) {
      return
    }

    model.remove(viewRows.map { table.convertRowIndexToModel(it) })
    if (table.rowCount > 0) {
      val row = minOf(viewRows.min(), table.rowCount - 1)
      table.setRowSelectionInterval(row, row)
      ScrollingUtil.ensureIndexIsVisible(table, row, 0)
    }
  }

  private fun updateDetails() {
    val viewRow = table.selectedRow
    if (viewRow < 0) {
      details.text = null
      return
    }
    details.text = when (val entry = model.getEntry(table.convertRowIndexToModel(viewRow))) {
      is StringEntry -> entry.value
      is ListEntry -> entry.values.joinToString("\n")
    }
    details.caretPosition = 0
  }

  private inner class EditAction : DumbAwareAction(PlatformInternalBundle.messagePointer("properties.component.action.edit"), AllIcons.Actions.EditSource) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getEditSource(), table)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = canChangeSelection() && table.selectedRowCount == 1
    }

    override fun actionPerformed(e: AnActionEvent) {
      edit(table.selectedRow)
    }
  }

  private inner class RemoveAction : DumbAwareAction(PlatformInternalBundle.messagePointer("properties.component.action.remove"), AllIcons.General.Remove) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getDelete(), table)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = canChangeSelection() && table.selectedRowCount > 0
    }

    override fun actionPerformed(e: AnActionEvent) {
      removeSelected()
    }
  }

  private inner class RefreshAction : DumbAwareAction(PlatformInternalBundle.messagePointer("properties.component.action.refresh"), AllIcons.Actions.Refresh) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getRerun(), table)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = !table.isEditing
    }

    override fun actionPerformed(e: AnActionEvent) {
      model.reload()
      ScrollingUtil.ensureSelectionExists(table)
    }
  }
}

private sealed interface PropertyEntry {
  val key: String
}

private data class StringEntry(override val key: String, val value: String) : PropertyEntry

private data class ListEntry(override val key: String, val values: List<String>) : PropertyEntry

/**
 * Keeps a snapshot of the [properties] entries and writes each change to [properties] at once.
 *
 * The model calls [onChanged] after the table processes each change event.
 */
private class PropertiesTableModel(private val properties: PropertiesComponent, private val onChanged: () -> Unit) : AbstractTableModel() {
  private val entries = ArrayList<PropertyEntry>()

  val isSupported: Boolean
    get() = properties is BasePropertyService

  fun reload() {
    entries.clear()
    val state = (properties as? BasePropertyService)?.state
    if (state != null) {
      state.keyToString.mapTo(entries) { (key, value) -> StringEntry(key, value) }
      state.keyToStringList.mapTo(entries) { (key, values) -> ListEntry(key, values) }
    }
    fireTableDataChanged()
    onChanged()
  }

  fun getEntry(row: Int): PropertyEntry = entries[row]

  override fun getRowCount(): Int = entries.size

  override fun getColumnCount(): Int = 3

  override fun getColumnName(column: Int): String {
    return when (column) {
      KEY_COLUMN -> PlatformInternalBundle.message("properties.component.column.key")
      VALUE_COLUMN -> PlatformInternalBundle.message("properties.component.column.value")
      else -> PlatformInternalBundle.message("properties.component.column.type")
    }
  }

  override fun getValueAt(row: Int, column: Int): Any {
    val entry = entries[row]
    return when (column) {
      KEY_COLUMN -> entry.key
      VALUE_COLUMN -> when (entry) {
        is StringEntry -> StringUtil.escapeLineBreak(entry.value)
        is ListEntry -> entry.values.joinToString(", ")
      }
      else -> when (entry) {
        is StringEntry -> PlatformInternalBundle.message("properties.component.type.string")
        is ListEntry -> PlatformInternalBundle.message("properties.component.type.list")
      }
    }
  }

  /**
   * Returns `true` only for a single-line string value, because a text field cannot keep a line break.
   */
  override fun isCellEditable(row: Int, column: Int): Boolean {
    val entry = entries[row]
    return column == VALUE_COLUMN && entry is StringEntry && !entry.value.contains('\n')
  }

  override fun setValueAt(value: Any?, row: Int, column: Int) {
    if (column == VALUE_COLUMN && value is String) {
      setText(row, value)
    }
  }

  /**
   * Sets the value of the entry at [row] from [text]. For a list entry, each line of [text] is one item.
   */
  fun setText(row: Int, text: String) {
    val newEntry = when (val entry = entries[row]) {
      is StringEntry -> {
        if (entry.value == text) return
        properties.setValue(entry.key, text)
        entry.copy(value = text)
      }
      is ListEntry -> {
        val values = if (text.isEmpty()) emptyList() else text.lines()
        if (entry.values == values) return
        properties.setList(entry.key, values)
        entry.copy(values = values)
      }
    }
    entries[row] = newEntry
    fireTableRowsUpdated(row, row)
    onChanged()
  }

  fun remove(rows: Collection<Int>) {
    for (row in rows.sortedDescending()) {
      when (val entry = entries.removeAt(row)) {
        is StringEntry -> properties.unsetValue(entry.key)
        is ListEntry -> properties.setList(entry.key, null)
      }
    }
    fireTableDataChanged()
    onChanged()
  }
}

private class TextRenderer : ColoredTableCellRenderer() {
  override fun customizeCellRenderer(table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    @NlsSafe val text = value?.toString() ?: return
    append(text)
    SpeedSearchUtil.applySpeedSearchHighlighting(table, this, true, selected)
  }
}

private class MultilineValueDialog(parent: JComponent, private val entry: PropertyEntry) : DialogWrapper(parent, true) {
  private lateinit var textArea: JBTextArea

  val text: String
    get() = textArea.text

  init {
    title = PlatformInternalBundle.message("properties.component.edit.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val message = when (entry) {
      is StringEntry -> PlatformInternalBundle.message("properties.component.edit.value.message", entry.key)
      is ListEntry -> PlatformInternalBundle.message("properties.component.edit.list.message", entry.key)
    }
    val initialText = when (entry) {
      is StringEntry -> entry.value
      is ListEntry -> entry.values.joinToString("\n")
    }
    return panel {
      row {
        label(message)
      }
      row {
        textArea = textArea()
          .text(initialText)
          .rows(12)
          .columns(COLUMNS_LARGE)
          .align(Align.FILL)
          .focused()
          .component
      }.resizableRow()
    }
  }
}
