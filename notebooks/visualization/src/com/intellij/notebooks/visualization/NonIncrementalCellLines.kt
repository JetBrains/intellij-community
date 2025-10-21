package com.intellij.notebooks.visualization

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.EditorDocumentPriorities
import com.intellij.openapi.util.TextRange
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil

/**
 * inspired by [org.jetbrains.plugins.notebooks.editor.NotebookCellLinesImpl],
 * calculates all markers and intervals from scratch for each document update
 */
class NonIncrementalCellLines private constructor(
  document: Document,
  private val intervalsGenerator: IntervalsGenerator,
) : NotebookCellLines {

  override var intervals: List<NotebookCellLines.Interval> = intervalsGenerator.makeIntervals(document)

  private val documentListener = createDocumentListener()

  override val intervalListeners: EventDispatcher<NotebookCellLines.IntervalListener> =
    EventDispatcher.create(NotebookCellLines.IntervalListener::class.java)

  override var modificationStamp: Long = 0

  init {
    document.addDocumentListener(documentListener)
  }

  override fun intervalsIterator(startLine: Int): ListIterator<NotebookCellLines.Interval> {
    // ToDo temporary commented, while we are working on PY-76052.
    //ThreadingAssertions.softAssertReadAccess()
    val ordinal = intervals.find { startLine <= it.lines.last }?.ordinal ?: intervals.size
    return intervals.listIterator(ordinal)
  }

  private fun createEvent(
    oldCells: List<NotebookCellLines.Interval>,
    newCells: List<NotebookCellLines.Interval>,
    oldAffectedCells: List<NotebookCellLines.Interval>,
    newAffectedCells: List<NotebookCellLines.Interval>,
    documentEvent: DocumentEvent,
  ): NotebookCellLinesEvent {
    val (trimmedOldCells, trimmedNewCells) =
      if (oldCells == newCells) {
        Pair(emptyList(), emptyList())
      }
      else {
        ++modificationStamp

        val trimAtBegin = oldCells.zip(newCells).takeWhile { (oldCell, newCell) ->
          oldCell == newCell &&
          oldCell != oldAffectedCells.firstOrNull() && newCell != newAffectedCells.firstOrNull()
        }.count()

        val trimAtEnd = oldCells.asReversed().zip(newCells.asReversed()).takeWhile { (oldCell, newCell) ->
          oldCell.type == newCell.type &&
          oldCell.language == newCell.language &&
          oldCell.size == newCell.size &&
          oldCell != oldAffectedCells.lastOrNull() && newCell != newAffectedCells.lastOrNull()
        }.count()

        Pair(trimmed(oldCells, trimAtBegin, trimAtEnd), trimmed(newCells, trimAtBegin, trimAtEnd))
      }

    val event = NotebookCellLinesEvent(
      documentEvent = documentEvent,
      oldIntervals = trimmedOldCells,
      oldAffectedIntervals = oldAffectedCells,
      newIntervals = trimmedNewCells,
      newAffectedIntervals = newAffectedCells,
      modificationStamp = modificationStamp,
    )
    return event
  }

  private fun notify(event: NotebookCellLinesEvent) {
    catchThrowableAndLog {
      intervalListeners.multicaster.documentChanged(event)
    }
  }

  private fun createDocumentListener() = object : PrioritizedDocumentListener {
    private var oldAffectedCells: List<NotebookCellLines.Interval> = emptyList()

    override fun getPriority(): Int = EditorDocumentPriorities.INLAY_MODEL + 1

    override fun beforeDocumentChange(event: DocumentEvent) {
      oldAffectedCells = getAffectedCells(intervals, event.document, TextRange(event.offset, event.offset + event.oldLength))

      catchThrowableAndLog {
        intervalListeners.multicaster.beforeDocumentChange(
          NotebookCellLinesEventBeforeChange(
            documentEvent = event,
            oldAffectedIntervals = oldAffectedCells,
            modificationStamp = modificationStamp
          )
        )
      }
    }

    override fun documentChanged(event: DocumentEvent) {
      // ToDo temporary commented, while we are working on PY-76052.
      //ThreadingAssertions.assertWriteAccess()
      val oldIntervals = intervals

      intervals = intervalsGenerator.makeIntervals(event.document, event)
      val newAffectedCells = getAffectedCells(intervals, event.document, TextRange(event.offset, event.offset + event.newLength))
      val newEvent = createEvent(oldIntervals, intervals, oldAffectedCells, newAffectedCells, event)
      notify(newEvent)
    }

    override fun bulkUpdateFinished(document: Document) {
      catchThrowableAndLog {
        intervalListeners.multicaster.bulkUpdateFinished()
      }
    }
  }

  private inline fun catchThrowableAndLog(func: () -> Unit) {
    try {
      func()
    }
    catch (t: Throwable) {
      thisLogger().error(NonIncrementalCellLinesException("NotebookCellLines.IntervalListener shouldn't throw exceptions", t))
      // Wrap and consume exception even if it is control flow exception, otherwise this will prevent document updating. See DS-4305
    }
  }

  companion object {
    private val map = ContainerUtil.createConcurrentWeakMap<Document, NotebookCellLines>()

    fun get(document: Document, intervalsGenerator: IntervalsGenerator): NotebookCellLines =
      map.computeIfAbsent(document) {
        NonIncrementalCellLines(document, intervalsGenerator)
      }

    fun getOrNull(document: Document): NotebookCellLines? =
      map[document]
  }
}

class NonIncrementalCellLinesException(msg: String, t: Throwable) : RuntimeException(msg, t)

private fun <T> trimmed(list: List<T>, trimAtBegin: Int, trimAtEnd: Int) =
  list.subList(trimAtBegin, list.size - trimAtEnd)

private val NotebookCellLines.Interval.size: Int
  get() = lines.last + 1 - lines.first

private fun getAffectedCells(
  intervals: List<NotebookCellLines.Interval>,
  document: Document,
  textRange: TextRange,
): List<NotebookCellLines.Interval> {
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