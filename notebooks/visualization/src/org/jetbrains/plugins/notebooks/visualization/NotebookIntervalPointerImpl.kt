package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.util.EventDispatcher

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
  override val changeListeners: EventDispatcher<NotebookIntervalPointerFactory.ChangeListener> =
    EventDispatcher.create(NotebookIntervalPointerFactory.ChangeListener::class.java)

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
    when {
      oldIntervals.isEmpty() && newIntervals.isEmpty() -> {
        // content edited without affecting intervals values
        onEdited(eventAffectedIntervals)
      }
      oldIntervals.size == 1 && newIntervals.size == 1 && oldIntervals.first().type == newIntervals.first().type -> {
        // only one interval changed size
        pointers[newIntervals.first().ordinal].interval = newIntervals.first()
        onEdited(eventAffectedIntervals)
      }
      else -> {
        pointers.removeAll(oldIntervals.mapTo(hashSetOf()) {
          pointers[it.ordinal].also { pointer ->
            pointer.interval = null
            changeListeners.multicaster.onRemoved(it.ordinal)
          }
        })

        newIntervals.firstOrNull()?.also { firstNew ->
          pointers.addAll(firstNew.ordinal, newIntervals.map { NotebookIntervalPointerImpl(it) })
        }
        for (newInterval in newIntervals) {
          changeListeners.multicaster.onInserted(newInterval.ordinal)
        }
        onEdited(eventAffectedIntervals, excluded = newIntervals)
      }
    }

    val invalidPointersStart =
      newIntervals.firstOrNull()?.let { it.ordinal + newIntervals.size }
      ?: oldIntervals.firstOrNull()?.ordinal
      ?: pointers.size

    updatePointersFrom(invalidPointersStart)

    applyChanges()
  }

  private fun onEdited(intervals: List<NotebookCellLines.Interval>, excluded: List<NotebookCellLines.Interval> = emptyList()) {
    if (intervals.isEmpty()) return

    val overLast = intervals.last().ordinal + 1
    val excludedRange = (excluded.firstOrNull()?.ordinal ?: overLast)..(excluded.lastOrNull()?.ordinal ?: overLast)

    for (interval in intervals) {
      if (interval.ordinal !in excludedRange) {
        changeListeners.multicaster.onEdited(interval.ordinal)
      }
    }
  }

  private fun applyChanges() {
    mySavedChanges?.forEach { hint ->
      when (hint) {
        is NotebookIntervalPointerFactory.Invalidate -> {
          val ordinal = hint.ptr.get()?.ordinal
          invalidate((hint.ptr as NotebookIntervalPointerImpl))
          if (ordinal != null) {
            changeListeners.multicaster.let { listener ->
              listener.onRemoved(ordinal)
              listener.onInserted(ordinal)
            }
          }
        }
        is NotebookIntervalPointerFactory.Reuse -> {
          val oldPtr = hint.ptr as NotebookIntervalPointerImpl
          pointers.getOrNull(hint.ordinalAfterChange)?.let { newPtr ->
            if (oldPtr !== newPtr) {
              val oldOrdinal = oldPtr.interval?.ordinal
              invalidate(oldPtr)
              oldPtr.interval = newPtr.interval
              newPtr.interval = null
              pointers[hint.ordinalAfterChange] = oldPtr
              if (oldOrdinal != null && oldOrdinal != hint.ordinalAfterChange) {
                changeListeners.multicaster.onMoved(oldOrdinal, hint.ordinalAfterChange)
              }
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