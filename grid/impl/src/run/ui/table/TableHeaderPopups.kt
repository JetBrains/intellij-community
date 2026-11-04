package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Point
import javax.swing.SwingUtilities

/**
 * The header popups of a [TableResultView]. The column popup opens from either table's header and from a transposed gutter. The row popup
 * opens from a transposed header and from the gutter. It keeps the column the open column popup is about, which the column actions work
 * on, and the point it opened at.
 */
internal class TableHeaderPopups(
  grid: DataGrid,
  /** The column popup's actions; an empty group opens no column popup. */
  private val columnActions: ActionGroup,
  /** The row popup's actions; an empty group opens no row popup. */
  private val rowActions: ActionGroup,
) {
  /** The column index that stands for no column: -1. */
  private val noColumn: ModelIndex<GridColumn> = ModelIndex.forColumn(grid, -1)

  /** The column the open column popup is about, which the column actions work on; an index of -1 while no popup is open. */
  var contextColumn: ModelIndex<GridColumn> = noColumn
    private set

  /** The table whose data the open column popup's actions get, or null. */
  private var popupTable: GridTable? = null

  /** Where the open column popup opened, in its invoker's coordinates, or null. */
  private var popupPoint: Point? = null

  /** The point the open column popup opened at, while that popup's actions get [table]'s data; null otherwise. */
  fun contextMenuPoint(table: GridTable): Point? = if (table === popupTable) popupPoint else null

  /**
   * Opens the column popup about [column] at [point] of [invoker]. Its actions get the data of [actionTable]: the table whose header was
   * clicked, or the main table for a transposed gutter.
   */
  @Suppress("SplitModeApiUsage") // Until DBE-27115 splits the grid module into frontend and backend parts.
  fun showColumnPopup(column: ModelIndex<GridColumn>, actionTable: GridTable, invoker: Component, point: Point) {
    if (columnActions === ActionGroup.EMPTY_GROUP) return
    contextColumn = column
    popupTable = actionTable
    popupPoint = point
    val dataContext = DataManager.getInstance().getDataContext(actionTable)
    val popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, columnActions, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        // The chosen action runs after the popup closes, so the column is forgotten later, unless a popup about another column opened.
        SwingUtilities.invokeLater {
          if (contextColumn != column) return@invokeLater
          contextColumn = noColumn
          if (popupTable === actionTable) {
            popupTable = null
            popupPoint = null
          }
        }
      }
    })
    popup.show(RelativePoint(invoker, point))
  }

  /** Opens the row popup of a transposed header or of the gutter at [point] of [invoker]. */
  fun showRowPopup(invoker: Component, point: Point) {
    if (rowActions === ActionGroup.EMPTY_GROUP) return
    val popupMenu = ActionManager.getInstance().createActionPopupMenu("TableResultViewHeader", rowActions)
    popupMenu.component.show(invoker, point.x, point.y)
  }

  /** Stands in for an open column popup about [column], for tests that run its actions directly. */
  @TestOnly
  fun setContextColumnForTests(column: ModelIndex<GridColumn>) {
    contextColumn = column
  }
}
