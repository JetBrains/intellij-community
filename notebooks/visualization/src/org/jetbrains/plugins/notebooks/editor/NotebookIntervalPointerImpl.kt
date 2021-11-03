package org.jetbrains.plugins.notebooks.editor


private class NotebookIntervalPointerImpl(var interval: NotebookCellLines.Interval?): NotebookIntervalPointer {
  override fun get(): NotebookCellLines.Interval? = interval

  override fun toString(): String = "NotebookIntervalPointerImpl($interval)"
}


class NotebookIntervalPointerFactoryImpl(private val notebookCellLines: NotebookCellLines) : NotebookIntervalPointerFactory, NotebookCellLines.IntervalListener {
  private val pointers = ArrayList<NotebookIntervalPointerImpl>()
  private var savedHints: Iterable<NotebookIntervalPointerFactory.Hint>? = null

  init {
    pointers.addAll(notebookCellLines.intervals.asSequence().map { NotebookIntervalPointerImpl(it) })
    notebookCellLines.intervalListeners.addListener(this)
  }

  override fun create(interval: NotebookCellLines.Interval): NotebookIntervalPointer =
    pointers[interval.ordinal].also {
      require(it.interval == interval)
    }

  override fun <T> withHints(hints: Iterable<NotebookIntervalPointerFactory.Hint>, modifyDocumentAction: () -> T): T {
    try {
      require(savedHints == null) { "NotebookIntervalPointerFactory hints already added somewhere" }
      savedHints = hints
      return modifyDocumentAction().also {
        if (savedHints != null) {
          applyHints()
        }
      }
    }
    finally {
      savedHints = null
    }
  }

  override fun segmentChanged(oldIntervals: List<NotebookCellLines.Interval>, newIntervals: List<NotebookCellLines.Interval>) {
    val isOneIntervalResized = oldIntervals.size == 1 && newIntervals.size == 1 && oldIntervals.first().type == newIntervals.first().type

    if (isOneIntervalResized) {
      pointers[newIntervals.first().ordinal].interval = newIntervals.first()
    }
    else {
      pointers.removeAll(oldIntervals.map {
        pointers[it.ordinal].also { pointer ->
          pointer.interval = null
        }
      }.toSet())

      newIntervals.firstOrNull()?.also { firstNew ->
        pointers.addAll(firstNew.ordinal, newIntervals.map { NotebookIntervalPointerImpl(it) })
      }
    }

    val invalidPointersStart =
      newIntervals.firstOrNull()?.let { it.ordinal + newIntervals.size }
      ?: oldIntervals.firstOrNull()?.ordinal
      ?: pointers.size

    updatePointersFrom(invalidPointersStart)

    applyHints()
  }

  private fun applyHints() {
    savedHints?.forEach { hint ->
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
    savedHints = null
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