/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.paging

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.ComboBox
import org.jetbrains.plugins.notebooks.visualization.r.VisualizationBundle
import org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.DataFrame
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.DataFrameTableModel
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.PagingTableModel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.RowSorterEvent
import javax.swing.table.AbstractTableModel

class TablePaginator : JPanel(BorderLayout()) {

  private val rowsNumber = ComboBox<Int>(arrayOf(10, 15, 30, 100))

  private lateinit var toFirst: ActionButton
  private lateinit var toPrevious: ActionButton
  private lateinit var toNext: ActionButton
  private lateinit var toLast: ActionButton

  private var currentPage = JTextField()
  private val totalPages = JLabel()

  /* Flag was set inside update from action buttons to skip logic from text field value change. */
  private var currentPageChanging = false

  val label = JLabel()

  var table: JTable? = null
    set(value) {
      if (field == value) {
        return
      }

      cleanUp()
      setUp(value)
      field = value
    }

  var tableModel: PagingTableModel? = null

  init {
    createActionButtons()

    currentPage.document.addDocumentListener(object : DocumentListener {

      private fun currentPageIndex(): Int {
        val index = currentPage.text.toIntOrNull()
        if (index == null) {
          return tableModel!!.pageOffset
        }

        return index
      }

      private fun updatePageOffset() {
        if (currentPageChanging)
          return
        tableModel?.pageOffset = currentPageIndex() - 1
        label.text = VisualizationBundle.message("paginator.displaying", tableModel!!.pageOffset * tableModel!!.pageSize + 1,
                                                 tableModel!!.pageOffset * tableModel!!.pageSize + tableModel!!.rowCount,
                                                 tableModel!!.getRealRowCount())
      }

      override fun changedUpdate(e: DocumentEvent?) {
        updatePageOffset()
      }

      override fun insertUpdate(e: DocumentEvent?) {
        updatePageOffset()
      }

      override fun removeUpdate(e: DocumentEvent?) {
        updatePageOffset()
      }
    })

    rowsNumber.isEditable = true

    val editorComponent = rowsNumber.editor.editorComponent as? JComponent
    editorComponent?.putClientProperty("AuxEditorComponent", true)

    rowsNumber.addActionListener {
      if(rowsNumber.selectedItem is Int) {
        tableModel?.pageSize = rowsNumber.selectedItem as Int
        updateInfo()
      }
    }


    val rightPanel = JPanel()
    rightPanel.add(rowsNumber)
    add(rightPanel, BorderLayout.LINE_END)

    val panel = JPanel(FlowLayout(FlowLayout.LEFT))

    //panel.add(JToolBar.Separator())

    panel.add(toFirst)
    panel.add(toPrevious)

    panel.add(JToolBar.Separator())

    panel.add(JLabel(VisualizationBundle.message("paginator.label.page")))

    currentPage.putClientProperty("AuxEditorComponent", true)

    panel.add(currentPage)
    panel.add(totalPages)

    panel.add(JToolBar.Separator())
    panel.add(toNext)
    panel.add(toLast)

    panel.add(JToolBar.Separator())

    panel.add(label)

    add(panel, BorderLayout.LINE_START)
  }

  private fun cleanUp() {
    if (table == null) {
      return
    }

    if (parent != null) {
      val capturedParent = parent
      capturedParent.remove(this)
      capturedParent.revalidate()
    }

    val columnWidths = IntArray(table!!.columnCount) { 75 }
    for (i in 0 until table!!.columnCount) {
      columnWidths[i] = table!!.columnModel.getColumn(i).width
    }

    table!!.model = DataFrameTableModel(getDataFrame(table!!))

    for (i in 0 until table!!.columnCount) {
      table!!.columnModel.getColumn(i).preferredWidth = columnWidths[i]
    }
  }

  private fun getDataFrame(table: JTable): DataFrame {
    return if (table.model is DataFrameTableModel) {
      (table.model as DataFrameTableModel).dataFrame
    }
    else {
      (table.model as PagingTableModel).dataFrame
    }
  }

  private fun setUp(table: JTable?) {
    if (table == null) {
      return
    }

    // Table-ViewPort-ScrollPane-JPanel
    val parentPanel = table.parent?.parent?.parent as? JPanel ?: return

    parentPanel.add(this, BorderLayout.PAGE_END)
    parentPanel.revalidate()

    if (table.model is PagingTableModel) {
      tableModel = table.model as PagingTableModel
      //   rowsNumber.selectedItem = tableModel!!.pageSize
    }
    else {
      tableModel = PagingTableModel(getDataFrame(table))

      val columnWidths = IntArray(table.columnCount) { 75 }
      for (i in 0 until table.columnCount) {
        columnWidths[i] = table.columnModel.getColumn(i).width
      }

      table.model = tableModel

      for (i in 0 until table.columnCount) {
        table.columnModel.getColumn(i).preferredWidth = columnWidths[i]
      }

      tableModel!!.pageSize = rowsNumber.selectedItem as Int
    }

    if (table.rowSorter != null) {
      table.rowSorter.addRowSorterListener { e ->
        if (e.type == RowSorterEvent.Type.SORT_ORDER_CHANGED) {

          var sortColumn = -1
          var sortDescending = false
          val rowSorter = table.rowSorter
          if (rowSorter != null) {
            for (sortKey in rowSorter.sortKeys) {
              if (sortKey.sortOrder != SortOrder.UNSORTED) {
                sortColumn = sortKey.column
                sortDescending = sortKey.sortOrder == SortOrder.DESCENDING
                break

              }
            }
          }

          if (sortColumn != -1) {
            val dataFrame = getDataFrame(table)
            dataFrame.sortBy(dataFrame[sortColumn].name, sortDescending)
            (table.model as AbstractTableModel).fireTableDataChanged()
          }
        }
      }
    }

    updateInfo()
  }

  private fun updateInfo() {
    currentPageChanging = true
    label.text = VisualizationBundle.message("paginator.displaying", tableModel!!.pageOffset * tableModel!!.pageSize + 1,
                                                 tableModel!!.pageOffset * tableModel!!.pageSize + tableModel!!.rowCount,
                                                 tableModel!!.getRealRowCount())
    currentPage.text = (tableModel!!.pageOffset + 1).toString()
    totalPages.text = VisualizationBundle.message("paginator.of", tableModel!!.getPageCount().toString())
    currentPageChanging = false
  }

  private fun createActionButtons() {

    var action: AnAction = object : AnAction(VisualizationBundle.message("paginator.first.page.text"),
                                             VisualizationBundle.message("paginator.first.page.description"),
                                             AllIcons.Actions.Play_first) {
      override fun actionPerformed(e: AnActionEvent) {
        tableModel!!.pageOffset = 0
        updateInfo()
      }
    }
    toFirst = ActionButton(action, action.templatePresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

    action = object : AnAction(VisualizationBundle.message("paginator.previous.page.text"),
                               VisualizationBundle.message("paginator.previous.page.description"), AllIcons.Actions.Play_back) {
      override fun actionPerformed(e: AnActionEvent) {
        tableModel!!.pageOffset--
        updateInfo()
      }
    }
    toPrevious = ActionButton(action, action.templatePresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

    action = object : AnAction(VisualizationBundle.message("paginator.next.page.text"),
                               VisualizationBundle.message("paginator.next.page.description"), AllIcons.Actions.Play_forward) {
      override fun actionPerformed(e: AnActionEvent) {
        tableModel!!.pageOffset++
        updateInfo()
      }
    }
    toNext = ActionButton(action, action.templatePresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

    action = object : AnAction(VisualizationBundle.message("paginator.last.page.text"),
                               VisualizationBundle.message("paginator.last.page.description"), AllIcons.Actions.Play_last) {
      override fun actionPerformed(e: AnActionEvent) {
        tableModel!!.pageOffset = tableModel!!.getPageCount() - 1
        updateInfo()
      }
    }
    toLast = ActionButton(action, action.templatePresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }
}