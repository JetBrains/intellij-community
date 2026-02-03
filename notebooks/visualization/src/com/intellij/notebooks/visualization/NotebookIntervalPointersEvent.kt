package com.intellij.notebooks.visualization

/**
 * Passed to [NotebookIntervalPointerFactory.ChangeListener] in next cases:
 * * Underlying document is changed. (in such cases cellLinesEvent != null)
 * * Someone explicitly swapped two pointers or invalidated them by calling [NotebookIntervalPointerFactory.modifyPointers]
 * * one of upper changes was reverted or redone.
 *
 * Changes represented as a list of trivial changes. [Change]
 * Intervals which was just moved are not mentioned in changes. For example, when inserting code before them.
 */
data class NotebookIntervalPointersEvent(val isInBulkUpdate: Boolean,val changes: List<Change>) {


  data class PointerSnapshot(val pointer: NotebookIntervalPointer, val interval: NotebookCellLines.Interval)

  /**
   * Any change contains enough information to be inverted. It simplifies undo/redo actions.
   */
  sealed interface Change

  data class OnInserted(val subsequentPointers: List<PointerSnapshot>) : Change {
    val ordinals: IntRange = subsequentPointers.first().interval.ordinal..subsequentPointers.last().interval.ordinal
  }

  /* Snapshots contain intervals before removal */
  data class OnRemoved(val subsequentPointers: List<PointerSnapshot>) : Change {
    val ordinals: IntRange = subsequentPointers.first().interval.ordinal..subsequentPointers.last().interval.ordinal
  }

  data class OnEdited(val pointer: NotebookIntervalPointer,
                      val intervalBefore: NotebookCellLines.Interval,
                      val intervalAfter: NotebookCellLines.Interval) : Change {
    val ordinal: Int
      get() = intervalAfter.ordinal
  }

  /* Snapshots contain intervals after swap */
  data class OnSwapped(val first: PointerSnapshot, val second: PointerSnapshot) : Change {
    val firstOrdinal: Int
      get() = first.interval.ordinal

    val secondOrdinal: Int
      get() = second.interval.ordinal
  }
}