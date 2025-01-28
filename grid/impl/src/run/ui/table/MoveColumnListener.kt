package com.intellij.database.run.ui.table

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.*
import com.intellij.database.run.ui.grid.MoveColumnsRequestPlace
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumn

class MoveColumnListener(private val grid: DataGrid, private val tableResultView: TableResultView) : MouseListener, TableColumnModelListener {

  private var draggingState = DraggingState.NONE

  override fun mouseReleased(e: MouseEvent) {
    if (draggingState == DraggingState.MOVED) {
      val order = mutableListOf<TableColumn>()
      tableResultView.columnModel.columns.asIterator().forEach { order.add(it) }
      var from = -1
      var to = -1
      for (i in 0 until order.size - 1) {
        val cur = order[i].modelIndex
        val next = order[i + 1].modelIndex
        if (cur > next) {
          // The second adjacent inversion
          if (from != -1) {
            return
          }

          if (i + 2 < order.size && cur > order[i + 2].modelIndex) {
            from = cur
            to = next
          }
          else {
            from = next
            to = cur
          }
        }
      }

      // No adjacent inversion
      if (from == -1) {
        return
      }

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
          AdjustColumnsAfterMoveInData(grid, tableResultView, from, to),
        )),
        ModelIndex.forColumn(grid, from),
        ModelIndex.forColumn(grid, to)
      )
    }

    draggingState = DraggingState.NONE
  }

  override fun columnMoved(e: TableColumnModelEvent) {
    if (draggingState == DraggingState.GRABBED && e.fromIndex != e.toIndex) {
      draggingState = DraggingState.MOVED
    }
  }

  override fun mousePressed(e: MouseEvent) {
    draggingState = DraggingState.GRABBED
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