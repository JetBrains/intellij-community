package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.ResultViewSelectionListener
import com.intellij.openapi.application.WriteIntentReadAction
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener

/**
 * The grid's selection listeners, added through [TableResultView.addSelectionChangedListener]. They are notified of a change in the view's
 * row selection or column selection, which both tables share.
 *
 * A selection event outside a change reaches them at once. A change is a call of [runAsOneChange], or a change of the view's column model.
 * During one, the listeners hear of the selection once, when the outermost change ends, after both tables took it. Changes nest.
 */
internal class GridSelectionEvents(
  /** The view's row selection, which both tables share. */
  private val rowSelection: ListSelectionModel,
  /** The view's column model, which both tables select through and which reports the start and the end of each change. */
  columnModel: ViewColumnModel,
) {
  /** The column selection of the view's column model. */
  private val columnSelection: ListSelectionModel = columnModel.selectionModel
  private val listeners = ArrayList<ResultViewSelectionListener>()
  /** How many changes are running, one inside another; the listeners hear of them when the count drops back to zero. */
  private var changeDepth = 0
  /** Whether a selection event arrived during the running change. */
  private var changedDuringChange = false
  /** Whether the running change asked for a notification through [requestNotification]. */
  private var notificationRequested = false

  init {
    val selectionListener = ListSelectionListener { e -> selectionChanged(e.valueIsAdjusting) }
    columnSelection.addListSelectionListener(selectionListener)
    rowSelection.addListSelectionListener(selectionListener)
    columnModel.addChangeListener(object : ColumnModelChangeListener {
      override fun changeStarted() = beginChange()

      override fun changeFinished() = endChange()
    })
  }

  /** Adds [listener], called on each change with whether the selection is adjusting. */
  fun addListener(listener: ResultViewSelectionListener) {
    listeners.add(listener)
  }

  /** Runs [action] as one change: the listeners hear of its selection events once, when the outermost change ends. */
  fun runAsOneChange(action: Runnable) {
    beginChange()
    try {
      action.run()
    }
    finally {
      endChange()
    }
  }

  /** Asks for one notification that the selection is final, at the end of the running change, or at once outside any change. */
  fun requestNotification() {
    if (changeDepth > 0) notificationRequested = true else notifyListeners(isAdjusting = false)
  }

  private fun selectionChanged(isAdjusting: Boolean) {
    if (changeDepth > 0) changedDuringChange = true else notifyListeners(isAdjusting)
  }

  private fun beginChange() {
    changeDepth++
  }

  private fun endChange() {
    changeDepth--
    if (changeDepth > 0) return
    val requested = notificationRequested
    val changed = changedDuringChange
    notificationRequested = false
    changedDuringChange = false
    when {
      requested -> notifyListeners(isAdjusting = false)
      changed -> notifyListeners(rowSelection.valueIsAdjusting || columnSelection.valueIsAdjusting)
    }
  }

  private fun notifyListeners(isAdjusting: Boolean) {
    WriteIntentReadAction.run(Runnable {
      for (listener in listeners) {
        listener.selectionChanged(isAdjusting)
      }
    })
  }
}
