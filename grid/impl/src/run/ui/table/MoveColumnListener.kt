package com.intellij.database.run.ui.table

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.ui.grid.MoveColumnsRequestPlace
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener

class MoveColumnListener(private val grid: DataGrid, private val tableResultView: TableResultView) : MouseListener, TableColumnModelListener {

  private var draggingState = DraggingState.NONE
  private var orderBeforeDrag: List<Int> = emptyList()

  override fun mousePressed(e: MouseEvent) {
    draggingState = DraggingState.GRABBED
    orderBeforeDrag = primaryViewOrder()
  }

  override fun mouseReleased(e: MouseEvent) {
    val before = orderBeforeDrag
    val moved = draggingState == DraggingState.MOVED
    draggingState = DraggingState.NONE
    orderBeforeDrag = emptyList()
    if (!moved) return

    val after = primaryViewOrder()
    val column = movedColumn(before, after) ?: return
    val target = targetIndexInData(after, column) ?: return
    if (target == column) return

    val mutator = GridUtil.getColumnsMutator(grid)
    mutator?.moveColumn(
      GridRequestSource(MoveColumnsRequestPlace(
        grid,
        {
          object : AutoCloseable {
            init {
              tableResultView.isEditingBlocked = true
              grid.getPanel().getComponent().setLoadingText(DataGridBundle.message("DataView.updatingFile"))
              grid.getPanel().getComponent().startLoading()
            }

            override fun close() {
              grid.getPanel().getComponent().stopLoading()
              tableResultView.isEditingBlocked = false
            }
          }
        },
        AdjustColumnsAfterMoveInData(grid, tableResultView, column, target),
      )),
      ModelIndex.forColumn(grid, column),
      ModelIndex.forColumn(grid, target)
    )
  }

  /** Model indices in the primary table's column-model order, including zero-width pinned placeholders. */
  private fun primaryViewOrder(): List<Int> {
    val order = mutableListOf<Int>()
    tableResultView.columnModel.columns.asIterator().forEach { order.add(it.modelIndex) }
    return order
  }

  /** Model indices of the pinned columns, which are shown in the frozen strip and kept at zero width here. */
  private fun pinnedColumns(): Set<Int> {
    val pinned = mutableSetOf<Int>()
    tableResultView.columnModel.columns.asIterator().forEach { column ->
      if ((column as? TableResultViewColumn)?.isFrozenHidden == true) pinned.add(column.modelIndex)
    }
    return pinned
  }

  /** Finds a column whose removal makes both orders equal; an adjacent swap may have two candidates. */
  private fun movedColumn(before: List<Int>, after: List<Int>): Int? {
    if (before.size != after.size || before == after) return null
    for (index in before.indices) {
      val candidate = before[index]
      if (candidate == after[index]) continue
      if (before.filterNot { it == candidate } == after.filterNot { it == candidate }) return candidate
    }
    return null
  }

  /**
   * Resolves the data destination from the adjacent column in the same pinned/unpinned group.
   * View positions cannot be used directly because the view and data orders may differ.
   */
  private fun targetIndexInData(after: List<Int>, column: Int): Int? {
    val pinned = pinnedColumns()
    val peers = after.filter { (it in pinned) == (column in pinned) }
    val position = peers.indexOf(column)
    if (position < 0) return null
    val left = peers.getOrNull(position - 1)
    if (left != null) return if (left < column) left + 1 else left
    val right = peers.getOrNull(position + 1) ?: return null
    return if (right > column) right - 1 else right
  }

  override fun columnMoved(e: TableColumnModelEvent) {
    if (draggingState == DraggingState.GRABBED && e.fromIndex != e.toIndex) {
      draggingState = DraggingState.MOVED
    }
  }

  enum class DraggingState {
    NONE, GRABBED, MOVED
  }

  override fun mouseClicked(e: MouseEvent) {
  }

  override fun mouseEntered(e: MouseEvent) {
  }

  override fun mouseExited(e: MouseEvent) {
  }

  override fun columnAdded(e: TableColumnModelEvent) {
  }

  override fun columnRemoved(e: TableColumnModelEvent) {
  }

  override fun columnMarginChanged(e: ChangeEvent) {
  }

  override fun columnSelectionChanged(e: ListSelectionEvent) {
  }
}
