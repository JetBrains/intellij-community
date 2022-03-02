package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import com.intellij.util.EventDispatcher
import java.util.*

val NOTEBOOK_CELL_LINES_INTERVAL_DATA_KEY = DataKey.create<NotebookCellLines.Interval>("NOTEBOOK_CELL_LINES_INTERVAL")

/**
 * Incrementally iterates over Notebook document, calculates line ranges of cells using lexer.
 * Fast enough for running in EDT, but could be used in any other thread.
 *
 * Note: there's a difference between this model and the PSI model.
 * If a document starts not with a cell marker, this class treat the text before the first cell marker as a raw cell.
 * PSI model treats such cell as a special "stem" cell which is not a Jupyter cell at all.
 * We haven't decided which model is correct and which should be fixed. So, for now avoid using stem cells in tests,
 * while UI of PyCharm DS doesn't allow to create a stem cell at all.
 */
interface NotebookCellLines {
  enum class CellType {
    CODE, MARKDOWN, RAW
  }

  data class Marker(
    val ordinal: Int,
    val type: CellType,
    val offset: Int,
    val length: Int
  ) : Comparable<Marker> {
    override fun compareTo(other: Marker): Int = offset - other.offset
  }

  enum class MarkersAtLines(val hasTopLine: Boolean, val hasBottomLine: Boolean) {
    NO(false, false),
    TOP(true, false),
    BOTTOM(false, true),
    TOP_AND_BOTTOM(true, true)
  }

  data class Interval(
    val ordinal: Int,
    val type: CellType,
    val lines: IntRange,
    val markers: MarkersAtLines,
  ) : Comparable<Interval> {
    override fun compareTo(other: Interval): Int = lines.first - other.lines.first
  }

  interface IntervalListener : EventListener {
    /**
     * Called when document is changed.
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
    fun segmentChanged(oldIntervals: List<Interval>, oldAffectedIntervals: List<Interval>,
                       newIntervals: List<Interval>, newAffectedIntervals: List<Interval>)
  }

  fun intervalsIterator(startLine: Int = 0): ListIterator<Interval>

  val intervals: List<Interval>

  val intervalListeners: EventDispatcher<IntervalListener>

  val modificationStamp: Long

  companion object {
    fun get(editor: Editor): NotebookCellLines =
      editor.notebookCellLinesProvider?.create(editor.document)
      ?: error("Can't get for $editor with document ${editor.document}")

    fun hasSupport(editor: Editor): Boolean =
      editor.notebookCellLinesProvider != null
  }
}