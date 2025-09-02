package com.intellij.diagnostic.specialPaths

import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.diagnostic.specialPaths.SpecialPathEntry.Kind
import com.intellij.execution.ExecutionBundle
import com.intellij.ide.DataManager
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.JTableHeader
import kotlin.io.path.pathString

internal class BrowseSpecialPathsDialog(val project: Project?) : DialogWrapper(project) {
  companion object {
    private val columnNames = arrayOf("Description", "Path")
    private val columnPreferredWidths = intArrayOf(200, 900)
    private val defaultSize = Dimension(JBUI.scale(columnPreferredWidths.sum()), 500.px)

    private val Int.px: Int
      get() = JBUI.scale(this)
  }

  private fun SpecialPathEntry.getColumn(column: Int) = when (column) {
    0 -> name
    1 -> path
    else -> throw IndexOutOfBoundsException("column")
  }

  private val specialPaths = SpecialPathsProvider.EP_NAME.extensionList
    .flatMap { it.collectPaths(project) }
    .sortedBy { it.name }

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
    onMouseDoubleClicked(this) { e ->
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
    overrideKeyStroke(this, "alt ENTER", "alt ENTER") {
      val row = self.selectedRow
      if (row >= 0 && row < specialPaths.size) {
        val dataContext = DataManager.getInstance().getDataContext(this)
        val popup = createPopup(specialPaths[row], dataContext)
        popup.showInBestPositionFor(dataContext)
      }
    }
    TableSpeedSearch.installOn(this)
      .apply { comparator = SpeedSearchComparator(false) }
  }

  private fun createPopup(specialPathEntry: SpecialPathEntry, dataContext: DataContext) =
    JBPopupFactory.getInstance().createActionGroupPopup(
      DiagnosticBundle.message("popup.title.actions.with.alt.enter", specialPathEntry.name),
      specialPathEntry.getContextActionGroup(project) { close(CLOSE_EXIT_CODE) },
      dataContext,
      JBPopupFactory.ActionSelectionAid.NUMBERING,
      false
    )

  private val selectedRow: Int get() = table.selectedRow
  private val selectedPath: SpecialPathEntry? get() = if (tableModel.hasRow(selectedRow)) specialPaths[selectedRow] else null

  override fun createActions(): Array<Action> {
    val copyAllPathsToClipboard = object : AbstractAction(DiagnosticBundle.message("copy.all.to.clipboard")) {
      override fun actionPerformed(e: ActionEvent?) {
        val sb = StringBuilder()
        val maxNameLength = specialPaths.maxOfOrNull { it.name.length } ?: 0
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

  override fun createCenterPanel(): JPanel = JPanel(BorderLayout()).apply {
    add(JBScrollPane(table), BorderLayout.CENTER)
    preferredSize = defaultSize
  }

  override fun getPreferredFocusedComponent() = table

  /**
   * For Kind=File, it opens the containing folder
   * For Kind=Folder, it opens the folder.
   */
  private fun SpecialPathEntry.openDirectory() {
    val notNullPath = path ?: return
    when (kind) {
      Kind.Folder -> RevealFileAction.openDirectory(notNullPath)
      Kind.File -> RevealFileAction.openFile(notNullPath)
    }
  }

  private fun SpecialPathEntry.openInEditor(project: Project) {
    val notNullPath = path ?: return
    if (kind == Kind.Folder) {
      throw Exception("It's impossible to open folder in editor")
    }

    val file = VfsUtil.findFile(notNullPath, true)
    if (file == null) {
      Notifications.Bus.notify(
        Notification(
          "System Messages",
          ExecutionBundle.message("error.common.title"),
          DiagnosticBundle.message ("notification.content.there.no.such.file", path),
          NotificationType.ERROR
        ), project
      )
    }
    else {
      FileEditorManager.getInstance(project).openFile(file, true, true)
    }
  }

  private fun SpecialPathEntry.open(project: Project?) {
    when (kind) {
      Kind.Folder -> openDirectory()
      Kind.File -> if (project != null) {
        openInEditor(project)
      }
      else {
        openDirectory()
      }
    }
  }

  private fun SpecialPathEntry.getContextActionGroup(project: Project?, closeAction: () -> Unit) = DefaultActionGroup().apply {
    when (kind) {
      Kind.Folder -> {
        add(object : AnAction(DiagnosticBundle.messagePointer("action.open.folder.text"),
                              DiagnosticBundle.messagePointer("action.open.folder.description", path),
                              { null }) {
          override fun actionPerformed(e: AnActionEvent) {
            openDirectory()
            closeAction()
          }
        })
      }
      Kind.File -> {
        if (project != null) {
          add(object : AnAction(DiagnosticBundle.messagePointer("action.open.in.editor.text"),
                                DiagnosticBundle.messagePointer("action.open.in.editor.description", path),
              { null }) {
            override fun actionPerformed(e: AnActionEvent) {
              openInEditor(project)
              closeAction()
            }
          })
        }
        add(object : AnAction(DiagnosticBundle.messagePointer("action.show.in.folder.text"),
                              DiagnosticBundle.messagePointer("action.show.in.folder.description", path?.parent),
                              { null }) {
          override fun actionPerformed(e: AnActionEvent) {
            openDirectory()
            closeAction()
          }
        })
      }
    }
    add(object : AnAction(DiagnosticBundle.messagePointer("action.copy.path.text"),
                          DiagnosticBundle.messagePointer("action.copy.path.description", path),
                          { null }) {
      override fun actionPerformed(e: AnActionEvent) {
        val notNullPath = path ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(notNullPath.pathString))
      }
    })
  }

  private inline fun <reified T : JComponent> onMouseDoubleClicked(comp: T, crossinline handler: T.(MouseEvent) -> Unit) {
    comp.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2)
          handler(comp, e)
      }
    })
  }

  @Suppress("SameParameterValue")
  private fun overrideKeyStroke(c: JComponent, key: String, stroke: String, action: () -> Unit) {
    val inputMap = c.getInputMap(JComponent.WHEN_FOCUSED)
    inputMap.put(KeyStroke.getKeyStroke(stroke), key)
    c.actionMap.put(key, object : AbstractAction() {
      override fun actionPerformed(arg: ActionEvent) {
        action()
      }
    })
  }

  init {
    title = DiagnosticBundle.message("dialog.title.special.files.folders")
    init()
    pack()
  }
}