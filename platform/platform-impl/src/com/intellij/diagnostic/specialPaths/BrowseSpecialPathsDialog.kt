package com.jetbrains.rider.diagnostics

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.jetbrains.rider.settings.RdClientDotnetBundle
import com.jetbrains.rider.ui.RiderUI
import com.jetbrains.rider.ui.px
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.JTableHeader

class BrowseSpecialPathsDialog(val project: Project?) : DialogWrapper(project) {
  companion object {
    private val columnNames = arrayOf("Description", "Path")
    private val columnPreferredWidths = intArrayOf(200, 900)
    private val defaultSize = Dimension(JBUI.scale(columnPreferredWidths.sum()), 500.px)
  }

  private fun SpecialPathEntry.getColumn(column: Int) = when (column) {
    0 -> name
    1 -> path
    else -> throw IndexOutOfBoundsException("column")
  }

  private val specialPaths = SpecialPathsProvider.EP_NAME.extensionList.flatMap { it.collectPaths(project) }

  private val tableModel = object : AbstractTableModel() {
    override fun getRowCount() = specialPaths.size
    override fun getColumnCount() = columnNames.size
    override fun getColumnName(column: Int) = columnNames[column]
    override fun getColumnClass(columnIndex: Int): Class<out Any> = String::class.java
    override fun isCellEditable(rowIndex: Int, columnIndex: Int) = false

    override fun getValueAt(row: Int, column: Int) = if (hasRow(row)) specialPaths[row].getColumn(column) else ""
    fun hasRow(row: Int) = row >= 0 && row < specialPaths.size
  }

  private val table = object : JBTable(tableModel) {}.apply {
    val self = this
    createDefaultColumnsFromModel()
    for ((i, width) in columnPreferredWidths.withIndex())
      columnModel.getColumn(i).preferredWidth = JBUI.scale(width)
    tableHeader = JTableHeader(columnModel)
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    this.setDefaultRenderer(String::class.java, object : ColoredTableCellRenderer() {
      override fun customizeCellRenderer(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
      ) {
        if (value != null) {
          @NlsSafe
          val stringValue = value.toString()
          append(stringValue)
        }
        SpeedSearchUtil.applySpeedSearchHighlighting(self, this, true, selected)
      }
    })
    RiderUI.onMouseDoubleClicked(this) { e ->
      val row = self.rowAtPoint(e.point)
      if (row >= 0 && row < specialPaths.size) {
        specialPaths[row].open(project)
        close(CLOSE_EXIT_CODE)
      }
    }
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component?, x: Int, y: Int) {
        val row = self.rowAtPoint(Point(x, y))
        if (row >= 0 && row < specialPaths.size && comp != null) {
          val popup = createPopup(specialPaths[row], DataManager.getInstance().getDataContext(comp, x, y))
          popup.show(RelativePoint(comp, Point(x, y)))
        }
      }
    })
    RiderUI.overrideKeyStroke(this, "alt ENTER") {
      val row = self.selectedRow
      if (row >= 0 && row < specialPaths.size) {
        val dataContext = DataManager.getInstance().getDataContext(this)
        val popup = createPopup(specialPaths[row], dataContext)
        popup.showInBestPositionFor(dataContext)
      }
    }
    TableSpeedSearch(this).apply { comparator = SpeedSearchComparator(false) }
  }

  private fun createPopup(specialPathEntry: SpecialPathEntry, dataContext: DataContext) =
    JBPopupFactory.getInstance().createActionGroupPopup(
      RdClientDotnetBundle.message("popup.title.actions.with.alt.enter", specialPathEntry.name),
      specialPathEntry.getContextActionGroup(project) { close(CLOSE_EXIT_CODE) },
      dataContext,
      JBPopupFactory.ActionSelectionAid.NUMBERING,
      false
    )

  private val selectedRow: Int get() = table.selectedRow
  private val selectedPath: SpecialPathEntry? get() = if (tableModel.hasRow(selectedRow)) specialPaths[selectedRow] else null

  override fun createActions(): Array<Action> {
    val copyAllPathsToClipboard = object : AbstractAction(RdClientDotnetBundle.message("copy.all.to.clipboard")) {
      override fun actionPerformed(e: ActionEvent?) {
        val sb = StringBuilder()
        val maxNameLength = specialPaths.map { it.name.length }.maxOrNull() ?: 0
        for (entry in specialPaths) {
          sb.appendLine("${entry.name.padEnd(maxNameLength, ' ')}  ${entry.path}")
        }
        CopyPasteManager.getInstance().setContents(StringSelection(sb.toString()))
        close(CLOSE_EXIT_CODE)
      }
    }
    return arrayOf(copyAllPathsToClipboard, okAction, cancelAction)
  }

  override fun doOKAction() {
    selectedPath?.open(project)
    super.doOKAction()
  }

  override fun createCenterPanel() = JPanel(BorderLayout()).apply {
    add(JBScrollPane(table), BorderLayout.CENTER)
    preferredSize = defaultSize
  }

  override fun getPreferredFocusedComponent() = table

  init {
    title = RdClientDotnetBundle.message("dialog.title.special.files.folders")
    init()
    pack()
  }
}