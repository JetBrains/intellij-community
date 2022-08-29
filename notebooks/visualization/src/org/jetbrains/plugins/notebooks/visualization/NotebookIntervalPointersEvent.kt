package org.jetbrains.plugins.notebooks.visualization

data class NotebookIntervalPointersEvent(val changes: List<Change>,
                                         val cellLinesEvent: NotebookCellLinesEvent?) {

  sealed interface Change
  data class OnInserted(val ordinals: IntRange) : Change
  data class OnEdited(val ordinal: Int) : Change
  data class OnRemoved(val ordinals: IntRange) : Change
  data class OnSwapped(val firstOrdinal: Int, val secondOrdinal: Int) : Change
}