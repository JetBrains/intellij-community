package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.openapi.Disposable
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.SwingUtilities

/**
 * Offers the floating toolbar for a cell clicked in the main or the pinned table.
 * Hides it on a mouse press, when the pointer moves away, when a table loses focus or moves, or when the table it is shown in goes away.
 */
internal class TableFloatingToolbarController(private val primary: JTable, grid: DataGrid, cs: CoroutineScope) : Disposable {
  private val toolbar = TableFloatingToolbar(primary, grid, cs)

  /** The table and the cell the toolbar was last offered for, as view indices of that table. */
  private var offeredTable: JTable = primary
  private var offeredRow = 0
  private var offeredColumn = 0

  private val mouseListener: MouseAdapter = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      toolbar.hide()
    }

    override fun mouseClicked(e: MouseEvent) {
      val table = e.component as? JTable ?: return
      if (e.button != MouseEvent.BUTTON1 || table.selectedRowCount != 1) {
        return
      }

      val column = table.columnAtPoint(e.point)
      val row = table.rowAtPoint(e.point)
      if (row < 0 || row >= table.rowCount || column < 0 || column >= table.columnCount) {
        return
      }

      val cell = table.getCellRect(row, column, true)
      offeredTable = table
      offeredRow = row
      offeredColumn = column
      toolbar.show(table, Point(cell.x + cell.width, cell.y))
    }

    override fun mouseMoved(e: MouseEvent) {
      if (!toolbar.isShowing) {
        return
      }
      // The pointer may already have crossed into the other table, while the toolbar stays where it was shown.
      val point = if (e.component === offeredTable) e.point else SwingUtilities.convertPoint(e.component, e.point, offeredTable)
      val cellArea = offeredTable.getCellRect(offeredRow, offeredColumn, true)
      cellArea.grow(0, cellArea.height * 2)
      if (cellArea.contains(point)) {
        return
      }

      if (distanceSq(point, toolbar.bounds) > hidingDistanceSq) {
        toolbar.hide()
      }
    }
  }

  private val focusListener: FocusAdapter = object : FocusAdapter() {
    override fun focusLost(e: FocusEvent?) {
      toolbar.hide()
    }
  }

  private val componentListener: ComponentAdapter = object : ComponentAdapter() {
    override fun componentMoved(e: ComponentEvent?) {
      toolbar.hide()
    }
  }

  init {
    attach(primary)
  }

  /** Offers the toolbar for the cells of [table] too, such as the pinned table. */
  fun attach(table: JTable) {
    table.addMouseListener(mouseListener)
    table.addMouseMotionListener(mouseListener)
    table.addFocusListener(focusListener)
    table.addComponentListener(componentListener)
  }

  /** Stops offering the toolbar in [table], which is about to be discarded, and hides it if it is shown there. */
  fun detach(table: JTable) {
    removeListeners(table)
    if (offeredTable !== table) return
    toolbar.hide()
    offeredTable = primary
  }

  override fun dispose() {
    toolbar.hide()
    removeListeners(primary)
  }

  private fun removeListeners(table: JTable) {
    table.removeMouseListener(mouseListener)
    table.removeMouseMotionListener(mouseListener)
    table.removeFocusListener(focusListener)
    table.removeComponentListener(componentListener)
  }

  /** The component and the point the toolbar is requested at, or null while none is requested. */
  @TestOnly
  fun getRequestedDisplay(): Pair<JComponent, Point>? = toolbar.getRequestedDisplay()

  private companion object {
    val hidingDistanceSq by lazy { JBUIScale.scale(100) * JBUIScale.scale(100) }

    fun distanceSq(p: Point, r: Rectangle): Double {
      if (r.contains(p)) {
        return 0.0
      }

      val dMinX = p.x - r.minX
      val dMaxX = p.x - r.maxX
      val dx = if (dMaxX > 0) dMaxX else (if (dMinX > 0) 0.0 else -dMinX)

      val dMinY = p.y - r.minY
      val dMaxY = p.y - r.maxY
      val dy = if (dMaxY > 0) dMaxY else (if (dMinY > 0) 0.0 else -dMinY)

      return dx * dx + dy * dy
    }
  }
}
