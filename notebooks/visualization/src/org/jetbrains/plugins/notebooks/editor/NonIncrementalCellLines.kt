package org.jetbrains.plugins.notebooks.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.TextRange
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil

/**
 * inspired by [org.jetbrains.plugins.notebooks.editor.NotebookCellLinesImpl],
 * calculates all markers and intervals from scratch for each document update
 */
class NonIncrementalCellLines private constructor(private val document: Document,
                                                  private val cellLinesLexer: NotebookCellLinesLexer,
                                                  private val intervalsGenerator: (Document, List<NotebookCellLines.Marker>) -> List<NotebookCellLines.Interval>) : NotebookCellLines {

  private var markers: List<NotebookCellLines.Marker> = emptyList()
  private var intervals: List<NotebookCellLines.Interval> = emptyList()
  private val documentListener = createDocumentListener()
  override val intervalListeners = EventDispatcher.create(NotebookCellLines.IntervalListener::class.java)

  override val intervalsCount: Int
    get() = intervals.size

  override var modificationStamp: Long = 0
    private set

  init {
    document.addDocumentListener(documentListener)
    updateIntervalsAndMarkers()
  }

  override fun getIterator(ordinal: Int): ListIterator<NotebookCellLines.Interval> =
    intervals.listIterator(ordinal)

  override fun getIterator(interval: NotebookCellLines.Interval): ListIterator<NotebookCellLines.Interval> {
    check(interval == intervals[interval.ordinal])
    return intervals.listIterator(interval.ordinal)
  }

  override fun intervalsIterator(startLine: Int): ListIterator<NotebookCellLines.Interval> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val ordinal = intervals.find { startLine <= it.lines.last }?.ordinal ?: intervals.size
    return intervals.listIterator(ordinal)
  }

  override fun markersIterator(startOffset: Int): ListIterator<NotebookCellLines.Marker> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val ordinal = markers.find { startOffset == it.offset || startOffset < it.offset + it.length }?.ordinal ?: markers.size
    return markers.listIterator(ordinal)
  }

  private fun updateIntervalsAndMarkers() {
    markers = cellLinesLexer.markerSequence(document.charsSequence, 0, 0).toList()
    intervals = intervalsGenerator(document, markers)
  }

  private fun notifyChanged(oldCells: List<NotebookCellLines.Interval>,
                            oldAffectedCells: List<NotebookCellLines.Interval>,
                            newCells: List<NotebookCellLines.Interval>,
                            newAffectedCells: List<NotebookCellLines.Interval>) {
    if (oldCells == newCells) {
      return
    }

    val trimAtBegin = oldCells.zip(newCells).takeWhile { (oldCell, newCell) ->
      oldCell == newCell &&
      oldCell != oldAffectedCells.firstOrNull() && newCell != newAffectedCells.firstOrNull()
    }.count()

    val trimAtEnd = oldCells.asReversed().zip(newCells.asReversed()).takeWhile { (oldCell, newCell) ->
      oldCell.type == newCell.type && oldCell.size == newCell.size &&
      oldCell != oldAffectedCells.lastOrNull() && newCell != newAffectedCells.lastOrNull()
    }.count()

    ++modificationStamp

    intervalListeners.multicaster.segmentChanged(
      trimmed(oldCells, trimAtBegin, trimAtEnd),
      trimmed(newCells, trimAtBegin, trimAtEnd)
    )
  }

  private fun createDocumentListener() = object : DocumentListener {
    private var oldAffectedCells: List<NotebookCellLines.Interval> = emptyList()

    override fun beforeDocumentChange(event: DocumentEvent) {
      oldAffectedCells = getAffectedCells(intervals, document, TextRange(event.offset, event.offset + event.oldLength))
    }

    override fun documentChanged(event: DocumentEvent) {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      val oldIntervals = intervals
      updateIntervalsAndMarkers()

      val newAffectedCells = getAffectedCells(intervals, document, TextRange(event.offset, event.offset + event.newLength))
      notifyChanged(oldIntervals, oldAffectedCells, intervals, newAffectedCells)
    }
  }

  companion object {
    private val map = ContainerUtil.createConcurrentWeakMap<Document, NotebookCellLines>()

    fun get(document: Document, lexerProvider: NotebookCellLinesLexer,
            intervalsGenerator: (Document, List<NotebookCellLines.Marker>) -> List<NotebookCellLines.Interval>): NotebookCellLines =
      map.computeIfAbsent(document) {
        NonIncrementalCellLines(document, lexerProvider, intervalsGenerator)
      }
  }
}

private fun <T> trimmed(list: List<T>, trimAtBegin: Int, trimAtEnd: Int) =
  list.subList(trimAtBegin, list.size - trimAtEnd)

private val NotebookCellLines.Interval.size: Int
  get() = lines.last + 1 - lines.first

private fun getAffectedCells(intervals: List<NotebookCellLines.Interval>,
                             document: Document,
                             textRange: TextRange): List<NotebookCellLines.Interval> {
  val firstLine = document.getLineNumber(textRange.startOffset).let { line ->
    if (document.getLineEndOffset(line) == textRange.startOffset) line + 1 else line
  }

  val endLine = document.getLineNumber(textRange.endOffset).let { line ->
    if (document.getLineStartOffset(line) == textRange.endOffset) line - 1 else line
  }

  return intervals.dropWhile {
    it.lines.last < firstLine
  }.takeWhile {
    it.lines.first <= endLine
  }
}