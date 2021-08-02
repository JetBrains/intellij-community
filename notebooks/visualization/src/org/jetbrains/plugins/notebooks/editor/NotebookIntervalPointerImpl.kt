package org.jetbrains.plugins.notebooks.editor


private class NotebookIntervalPointerImpl(var interval: NotebookCellLines.Interval?): NotebookIntervalPointer {
  override fun get(): NotebookCellLines.Interval? = interval

  override fun toString(): String = "NotebookIntervalPointerImpl($interval)"
}


internal class NotebookIntervalPointerFactoryImpl(private val notebookCellLines: NotebookCellLines): NotebookIntervalPointerFactory, NotebookCellLines.IntervalListener {
  private val pointers = ArrayList<NotebookIntervalPointerImpl>()

  init {
    pointers.addAll(notebookCellLines.getIterator(0).asSequence().map { NotebookIntervalPointerImpl(it) })
    notebookCellLines.intervalListeners.addListener(this)
  }

  override fun create(interval: NotebookCellLines.Interval): NotebookIntervalPointer =
    pointers[interval.ordinal].also {
      require(it.interval == interval)
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
  }

  private fun updatePointersFrom(pos: Int) {
    val iterator = notebookCellLines.getIterator(pos)

    for(i in pos until pointers.size) {
      pointers[i].interval = iterator.next()
    }
  }
}