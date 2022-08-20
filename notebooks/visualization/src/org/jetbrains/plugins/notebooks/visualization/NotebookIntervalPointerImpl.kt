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

  override fun segmentChanged(e: NotebookCellLinesEvent) {
    when {
      !e.isIntervalsChanged() -> {
        // content edited without affecting intervals values
        onEdited((e.oldAffectedIntervals + e.newAffectedIntervals).distinct().sortedBy { it.ordinal })
      }
      e.oldIntervals.size == 1 && e.newIntervals.size == 1 && e.oldIntervals.first().type == e.newIntervals.first().type -> {
        // only one interval changed size
        pointers[e.newIntervals.first().ordinal].interval = e.newIntervals.first()
        onEdited(e.newAffectedIntervals)
        if (e.newIntervals.first() !in e.newAffectedIntervals) {
          changeListeners.multicaster.onEdited(e.newIntervals.first().ordinal)
        }
      }
      else -> {
        for (old in e.oldIntervals.asReversed()) {
          pointers[old.ordinal].interval = null
          pointers.removeAt(old.ordinal)
          // called in reversed order, so ordinals of previous cells remain actual
          changeListeners.multicaster.onRemoved(old.ordinal)
        }

        e.newIntervals.firstOrNull()?.also { firstNew ->
          pointers.addAll(firstNew.ordinal, e.newIntervals.map { NotebookIntervalPointerImpl(it) })
        }
        for (newInterval in e.newIntervals) {
          changeListeners.multicaster.onInserted(newInterval.ordinal)
        }
        onEdited(e.newAffectedIntervals, excluded = e.newIntervals)
      }
    }

    val invalidPointersStart =
      e.newIntervals.firstOrNull()?.let { it.ordinal + e.newIntervals.size }
      ?: e.oldIntervals.firstOrNull()?.ordinal
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