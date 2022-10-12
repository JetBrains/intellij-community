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
          val eventBuilder = NotebookIntervalPointersEventBuilder()
          applySavedChanges(eventBuilder)
          eventBuilder.applyEvent(changeListeners)
        }
      }
    }
    finally {
      mySavedChanges = null
    }
  }

  override fun documentChanged(e: NotebookCellLinesEvent) {
    val eventBuilder = NotebookIntervalPointersEventBuilder()

    when {
      !e.isIntervalsChanged() -> {
        // content edited without affecting intervals values
        eventBuilder.onEdited((e.oldAffectedIntervals + e.newAffectedIntervals).distinct().sortedBy { it.ordinal })
      }
      e.oldIntervals.size == 1 && e.newIntervals.size == 1 && e.oldIntervals.first().type == e.newIntervals.first().type -> {
        // only one interval changed size
        pointers[e.newIntervals.first().ordinal].interval = e.newIntervals.first()
        eventBuilder.onEdited(e.newAffectedIntervals)
        if (e.newIntervals.first() !in e.newAffectedIntervals) {
          eventBuilder.onEdited(e.newIntervals.first())
        }
      }
      else -> {
        for (old in e.oldIntervals.asReversed()) {
          pointers[old.ordinal].interval = null
          pointers.removeAt(old.ordinal)
          // called in reversed order, so ordinals of previous cells remain actual
          eventBuilder.onRemoved(old)
        }

        e.newIntervals.firstOrNull()?.also { firstNew ->
          pointers.addAll(firstNew.ordinal, e.newIntervals.map { NotebookIntervalPointerImpl(it) })
        }
        eventBuilder.onInserted(e.newIntervals)
        eventBuilder.onEdited(e.newAffectedIntervals, excluded = e.newIntervals)
      }
    }

    val invalidPointersStart =
      e.newIntervals.firstOrNull()?.let { it.ordinal + e.newIntervals.size }
      ?: e.oldIntervals.firstOrNull()?.ordinal
      ?: pointers.size

    updatePointersFrom(invalidPointersStart)

    applySavedChanges(eventBuilder)

    eventBuilder.applyEvent(changeListeners)
  }

  private fun applySavedChanges(eventBuilder: NotebookIntervalPointersEventBuilder) {
    mySavedChanges?.forEach { hint ->
      when (hint) {
        is NotebookIntervalPointerFactory.Invalidate -> applySavedChange(eventBuilder, hint)
        is NotebookIntervalPointerFactory.Reuse -> applySavedChange(eventBuilder, hint)
      }
    }
    mySavedChanges = null
  }

  private fun applySavedChange(eventBuilder: NotebookIntervalPointersEventBuilder,
                               hint: NotebookIntervalPointerFactory.Invalidate) {
    val ptr = hint.ptr as NotebookIntervalPointerImpl
    val interval = ptr.interval
    if (interval == null) return

    invalidate(ptr)
    eventBuilder.onRemoved(interval)
    eventBuilder.onInserted(interval)
  }

  private fun applySavedChange(eventBuilder: NotebookIntervalPointersEventBuilder,
                               hint: NotebookIntervalPointerFactory.Reuse) {
    val oldPtr = hint.ptr as NotebookIntervalPointerImpl
    val newPtr = pointers.getOrNull(hint.ordinalAfterChange) ?: return

    if (oldPtr === newPtr) return // nothing to do

    val oldOrdinal = oldPtr.interval?.ordinal
    invalidate(oldPtr)
    oldPtr.interval = newPtr.interval
    newPtr.interval = null
    pointers[hint.ordinalAfterChange] = oldPtr
    if (oldOrdinal != null && oldOrdinal != hint.ordinalAfterChange) {
      eventBuilder.onMoved(oldOrdinal, hint.ordinalAfterChange)
    }
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

private class NotebookIntervalPointersEventBuilder {
  val accumulatedChanges = mutableListOf<Change>()

  fun applyEvent(eventDispatcher: EventDispatcher<NotebookIntervalPointerFactory.ChangeListener>) {
    for (change in accumulatedChanges) {
      when (change) {
        is OnInserted -> eventDispatcher.multicaster.onInserted(change.ordinal)
        is OnEdited -> eventDispatcher.multicaster.onEdited(change.ordinal)
        is OnRemoved -> eventDispatcher.multicaster.onRemoved(change.ordinal)
        is OnMoved -> eventDispatcher.multicaster.onMoved(change.fromOrdinal, change.toOrdinal)
      }
    }
  }

  fun onEdited(interval: NotebookCellLines.Interval) {
    accumulatedChanges.add(OnEdited(interval.ordinal))
  }

  fun onEdited(intervals: List<NotebookCellLines.Interval>, excluded: List<NotebookCellLines.Interval> = emptyList()) {
    if (intervals.isEmpty()) return

    val overLast = intervals.last().ordinal + 1
    val excludedRange = (excluded.firstOrNull()?.ordinal ?: overLast)..(excluded.lastOrNull()?.ordinal ?: overLast)

    for (interval in intervals) {
      if (interval.ordinal !in excludedRange) {
        accumulatedChanges.add(OnEdited(interval.ordinal))
      }
    }
  }

  fun onRemoved(interval: NotebookCellLines.Interval) {
    accumulatedChanges.add(OnRemoved(interval.ordinal))
  }

  fun onInserted(interval: NotebookCellLines.Interval) {
    accumulatedChanges.add(OnInserted(interval.ordinal))
  }

  fun onInserted(intervals: List<NotebookCellLines.Interval>) {
    for (interval in intervals) {
      onInserted(interval)
    }
  }

  fun onMoved(fromOrdinal: Int, toOrdinal: Int) {
    accumulatedChanges.add(OnMoved(fromOrdinal, toOrdinal))
  }

  sealed interface Change
  data class OnInserted(val ordinal: Int) : Change
  data class OnEdited(val ordinal: Int) : Change
  data class OnRemoved(val ordinal: Int) : Change
  data class OnMoved(val fromOrdinal: Int, val toOrdinal: Int) : Change
}