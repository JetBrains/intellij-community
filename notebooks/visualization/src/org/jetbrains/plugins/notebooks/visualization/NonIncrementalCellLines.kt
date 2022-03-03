package org.jetbrains.plugins.notebooks.visualization

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
                                                  private val intervalsGenerator: (Document) -> List<NotebookCellLines.Interval>) : NotebookCellLines {

  override var intervals: List<NotebookCellLines.Interval> = intervalsGenerator(document)
    private set

  private val documentListener = createDocumentListener()
  override val intervalListeners = EventDispatcher.create(NotebookCellLines.IntervalListener::class.java)

  override var modificationStamp: Long = 0
    private set

  init {
    document.addDocumentListener(documentListener)
  }

  override fun intervalsIterator(startLine: Int): ListIterator<NotebookCellLines.Interval> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val ordinal = intervals.find { startLine <= it.lines.last }?.ordinal ?: intervals.size
    return intervals.listIterator(ordinal)
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
      intervals = intervalsGenerator(document)

      val newAffectedCells = getAffectedCells(intervals, document, TextRange(event.offset, event.offset + event.newLength))
      notifyChanged(oldIntervals, oldAffectedCells, intervals, newAffectedCells)
    }
  }

  companion object {
    private val map = ContainerUtil.createConcurrentWeakMap<Document, NotebookCellLines>()

    fun get(document: Document, intervalsGenerator: (Document) -> List<NotebookCellLines.Interval>): NotebookCellLines =
      map.computeIfAbsent(document) {
        NonIncrementalCellLines(document, intervalsGenerator)
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

  val endLine = run {
    val line = document.getLineNumber(textRange.endOffset)
    val isAtStartOfLine = document.getLineStartOffset(line) == textRange.endOffset
    // for example: "CELL2" => "cell1\nCELL2"
    // CELL2 wasn't modified, but textRange.endOffset = 6 and getLineNumber(6) == 1.
    // so line number should be decreased by 1
    val isAtTheDocumentEnd = document.textLength == textRange.endOffset
    // RMarkdown may contain empty md cell after last \n symbol.
    // for example, "```{r}\ncode\n```\n" has code cell at lines 0..2 and empty markdown cell at line 3
    // empty cell has text length==0 and should be marked as affected cell - it begins and ends at textRange.endOffset
    if (isAtStartOfLine && !isAtTheDocumentEnd) line - 1 else line
  }

  return intervals.dropWhile {
    it.lines.last < firstLine
  }.takeWhile {
    it.lines.first <= endLine
  }
}