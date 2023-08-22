package org.jetbrains.plugins.notebooks.visualization

/**
 * passed to [NotebookIntervalPointerFactory.ChangeListener] in next cases:
 * * Underlying document is changed. (in such case cellLinesEvent != null)
 * * Someone explicitly swapped two pointers or invalidated them by calling [NotebookIntervalPointerFactory.modifyPointers]
 * * one of upper changes was reverted or redone. See corresponding [EventSource]
 *
 * Changes represented as list of trivial changes. [Change]
 * Intervals which was just moved are not mentioned in changes. For example, when inserting code before them.
 */
data class NotebookIntervalPointersEvent(val changes: List<Change>,
                                         val cellLinesEvent: NotebookCellLinesEvent?,
                                         val source: EventSource) {
  enum class EventSource {
    ACTION, UNDO_ACTION, REDO_ACTION
  }


  data class PointerSnapshot(val pointer: NotebookIntervalPointer, val interval: NotebookCellLines.Interval)

  /**
   * any change contains enough information to be inverted. It simplifies undo/redo actions.
   */
  sealed interface Change

  data class OnInserted(val subsequentPointers: List<PointerSnapshot>) : Change {
    val ordinals = subsequentPointers.first().interval.ordinal..subsequentPointers.last().interval.ordinal
  }

  /* snapshots contains intervals before removal */
  data class OnRemoved(val subsequentPointers: List<PointerSnapshot>) : Change {
    val ordinals = subsequentPointers.first().interval.ordinal..subsequentPointers.last().interval.ordinal
  }

  data class OnEdited(val pointer: NotebookIntervalPointer,
                      val intervalBefore: NotebookCellLines.Interval,
                      val intervalAfter: NotebookCellLines.Interval) : Change {
    val ordinal: Int
      get() = intervalAfter.ordinal
  }

  /* snapshots contains intervals after swap */
  data class OnSwapped(val first: PointerSnapshot, val second: PointerSnapshot) : Change {
    val firstOrdinal: Int
      get() = first.interval.ordinal

    val secondOrdinal: Int
      get() = second.interval.ordinal
  }
}