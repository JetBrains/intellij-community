package com.intellij.database.run.ui.table

import com.intellij.openapi.application.WriteIntentReadAction
import java.util.function.Consumer
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener

/**
 * The grid's selection listeners, added through [TableResultView.addSelectionChangedListener]. They are notified of a change in the view's
 * row selection or column selection, which both tables share. When the grid's selection model sets rows and columns together,
 * [runWithoutEvents] silences them and [fireSelectionChanged] then notifies them once.
 */
internal class GridSelectionEvents(
  /** The view's row selection, which both tables share. */
  private val rowSelection: ListSelectionModel,
  /** The column selection of the slice that no table shows; it reports a column move once, after both tables took it. */
  private val columnSelection: ListSelectionModel,
) {
  private val listeners = ArrayList<Consumer<Boolean>>()
  /** True while [runWithoutEvents] runs. */
  private var eventsDisabled = false

  /** Adds [listener], called on each change with whether the selection is adjusting. */
  fun addListener(listener: Consumer<Boolean>) {
    val selectionListener = ListSelectionListener { e ->
      if (!eventsDisabled) {
        WriteIntentReadAction.run(Runnable {
          listener.accept(e.valueIsAdjusting)
        })
      }
    }
    columnSelection.addListSelectionListener(selectionListener)
    rowSelection.addListSelectionListener(selectionListener)
    listeners.add(listener)
  }

  /** Runs [action] without telling the listeners; call [fireSelectionChanged] afterwards to tell them once. */
  fun runWithoutEvents(action: Runnable) {
    try {
      eventsDisabled = true
      action.run()
    }
    finally {
      eventsDisabled = false
    }
  }

  /** Tells every listener of one finished change. */
  fun fireSelectionChanged() {
    for (listener in listeners) {
      listener.accept(false)
    }
  }
}
