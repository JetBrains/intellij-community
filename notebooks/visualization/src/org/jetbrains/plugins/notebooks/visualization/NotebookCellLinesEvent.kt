package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.event.DocumentEvent

/**
 * Passed to [NotebookCellLines.IntervalListener] when document is changed.
 *
 * Intervals that were just shifted are included neither [oldIntervals], nor in [newIntervals].
 *
 * Intervals in the lists are valid only until the document changes. Check their validity
 * when postponing handling of intervals.
 *
 * If document is changed, but intervals aren’t changed, both [oldIntervals] and [newIntervals] are empty,
 * and the [modificationStamp] stamp doesn’t change.
 *
 * [oldAffectedIntervals] and [oldAffectedIntervals] are same as event.oldFragment and event.newFragment
 * [oldAffectedIntervals] are intervals containing oldFragment and same with [newAffectedIntervals]
 *
 * If existing line is edited, [oldAffectedIntervals] and/or [newAffectedIntervals] contain interval with the line,
 * but [oldIntervals] and  [newIntervals] are empty.
 *
 * Both [oldAffectedIntervals] and [newAffectedIntervals] are necessary to distinguish last cell line removing and whole cell removing.
 * "#%%\n 1 \n\n#%%\n 2" -> "#%%\n 1 \n": [oldAffectedIntervals] contains second cell, [newAffectedIntervals] are empty
 *            ^^^^^^^^^
 * "#%%\n 1 \n text"     -> "#%%\n 1 \n": [oldAffectedIntervals] contain cell, [newAffectedIntervals] are empty
 *            ^^^^^
 * "#%%\n 1  text \n#%%\n 2" -> "#%% 1 \n#%%\n new": [oldAffectedIntervals] contain all cells,
 *           ^^^^^^^^^^^^^^            ^^^^^^^^^^^
 * [newAffectedIntervals] contain only second cell, because range of inserted text related to second cell only
 *
 * It is guaranteed that:
 * * Ordinals from every list defines an arithmetical progression where
 *   every next element has ordinal of the previous element incremented by one.
 * * If oldIntervals and newIntervals lists are not empty, the first elements of both lists has the same ordinal.
 * * Both lists don't contain any interval that has been only shifted, shrank or enlarged.
 *
 * See `NotebookCellLinesTest` for examples of calls for various changes.
 */
data class NotebookCellLinesEvent(
  val documentEvent: DocumentEvent,
  val oldIntervals: List<NotebookCellLines.Interval>,
  val oldAffectedIntervals: List<NotebookCellLines.Interval>,
  val newIntervals: List<NotebookCellLines.Interval>,
  val newAffectedIntervals: List<NotebookCellLines.Interval>,
  val modificationStamp: Long,
) {

  fun isIntervalsChanged(): Boolean =
    !(oldIntervals.isEmpty() && newIntervals.isEmpty())
}

/**
 * Passed to [NotebookCellLines.IntervalListener] before document change.
 * [modificationStamp] is old, version before change
 */
data class NotebookCellLinesEventBeforeChange(
  val documentEvent: DocumentEvent,
  val oldAffectedIntervals: List<NotebookCellLines.Interval>,
  val modificationStamp: Long,
)