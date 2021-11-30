package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Editor

class NotebookIntervalPointerFactoryImplProvider : NotebookIntervalPointerFactoryProvider {
  override fun create(editor: Editor): NotebookIntervalPointerFactory =
    NotebookIntervalPointerFactoryImpl(NotebookCellLines.get(editor))
}


private class NotebookIntervalPointerImpl(var interval: NotebookCellLines.Interval?) : NotebookIntervalPointer {
  override fun get(): NotebookCellLines.Interval? = interval

  override fun toString(): String = "NotebookIntervalPointerImpl($interval)"
}


class NotebookIntervalPointerFactoryImpl(private val notebookCellLines: NotebookCellLines) : NotebookIntervalPointerFactory, NotebookCellLines.IntervalListener {
  private val pointers = ArrayList<NotebookIntervalPointerImpl>()
  private var mySavedChanges: Iterable<NotebookIntervalPointerFactory.Change>? = null

  init {
    pointers.addAll(notebookCellLines.intervals.asSequence().map { NotebookIntervalPointerImpl(it) })
    notebookCellLines.intervalListeners.addListener(this)
  }

  override fun create(interval: NotebookCellLines.Interval): NotebookIntervalPointer =
    pointers[interval.ordinal].also {
      require(it.interval == interval)
    }

  override fun <T> modifyingPointers(changes: Iterable<NotebookIntervalPointerFactory.Change>, modifyDocumentAction: () -> T): T {
    try {
      require(mySavedChanges == null) { "NotebookIntervalPointerFactory hints already added somewhere" }
      mySavedChanges = changes
      return modifyDocumentAction().also {
        if (mySavedChanges != null) {
          applyChanges()
        }
      }
    }
    finally {
      mySavedChanges = null
    }
  }

  override fun segmentChanged(oldIntervals: List<NotebookCellLines.Interval>,
                              newIntervals: List<NotebookCellLines.Interval>,
                              eventAffectedIntervals: List<NotebookCellLines.Interval>) {
    if (oldIntervals.isEmpty() && newIntervals.isEmpty()) return

    val isOneIntervalResized = oldIntervals.size == 1 && newIntervals.size == 1 && oldIntervals.first().type == newIntervals.first().type

    if (isOneIntervalResized) {
      pointers[newIntervals.first().ordinal].interval = newIntervals.first()
    }
    else {
      pointers.removeAll(oldIntervals.mapTo(hashSetOf()) {
        pointers[it.ordinal].also { pointer ->
          pointer.interval = null
        }
      })

      newIntervals.firstOrNull()?.also { firstNew ->
        pointers.addAll(firstNew.ordinal, newIntervals.map { NotebookIntervalPointerImpl(it) })
      }
    }

    val invalidPointersStart =
      newIntervals.firstOrNull()?.let { it.ordinal + newIntervals.size }
      ?: oldIntervals.firstOrNull()?.ordinal
      ?: pointers.size

    updatePointersFrom(invalidPointersStart)

    applyChanges()
  }

  private fun applyChanges() {
    mySavedChanges?.forEach { hint ->
      when (hint) {
        is NotebookIntervalPointerFactory.Invalidate -> {
          invalidate((hint.ptr as NotebookIntervalPointerImpl))
        }
        is NotebookIntervalPointerFactory.Reuse -> {
          val oldPtr = hint.ptr as NotebookIntervalPointerImpl
          pointers.getOrNull(hint.ordinalAfterChange)?.let { newPtr ->
            if (oldPtr !== newPtr) {
              invalidate(oldPtr)
              oldPtr.interval = newPtr.interval
              newPtr.interval = null
              pointers[hint.ordinalAfterChange] = oldPtr
            }
          }
        }
      }
    }
    mySavedChanges = null
  }

  private fun invalidate(ptr: NotebookIntervalPointerImpl) {
    ptr.interval?.let { interval ->
      pointers[interval.ordinal] = NotebookIntervalPointerImpl(interval)
      ptr.interval = null
    }
  }

  private fun updatePointersFrom(pos: Int) {
    for (i in pos until pointers.size) {
      pointers[i].interval = notebookCellLines.intervals[i]
    }
  }
}