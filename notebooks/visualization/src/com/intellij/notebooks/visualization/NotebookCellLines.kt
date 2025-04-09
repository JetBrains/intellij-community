package com.intellij.notebooks.visualization

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.EventDispatcher
import com.intellij.util.keyFMap.KeyFMap
import java.util.*

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
    val data: KeyFMap, // different notebook implementations could store their own values in this map
  ) : Comparable<Interval> {
    val language: Language = data.get(INTERVAL_LANGUAGE_KEY)!!

    val firstContentLine: Int
      get() =
        if (markers.hasTopLine)
          lines.first + 1
        else lines.first

    val lastContentLine: Int
      get() =
        if (markers.hasBottomLine) lines.last - 1
        else lines.last

    val contentLines: IntRange
      get() = firstContentLine..lastContentLine

    operator fun <V> get(key: Key<V>): V? = data.get(key)

    override fun compareTo(other: Interval): Int = lines.first - other.lines.first

    fun getCellEndOffset(editor: Editor): Int {
      return getCellEndOffset(editor.document)
    }

    fun getCellStartOffset(editor: Editor): Int {
      return getCellStartOffset(editor.document)
    }

    fun getCellRange(editor: Editor): TextRange {
      val document = editor.document
      return getCellRange(document)
    }

    fun getCellRange(document: Document): TextRange {
      val startOffset = getCellStartOffset(document)
      val endOffset = getCellEndOffset(document)
      return TextRange(startOffset, endOffset)
    }

    fun getCellEndOffset(document: Document): Int {
      return document.getLineEndOffset(lines.last)
    }

    fun getCellStartOffset(document: Document): Int {
      return document.getLineStartOffset(lines.first)
    }

    fun getContentRange(editor: Editor): TextRange {
      val document = editor.document
      return getContentRange(document)
    }

    fun getContentRange(document: Document): TextRange {
      val startOffset = document.getLineStartOffset(contentLines.first)
      val endOffset = document.getLineEndOffset(contentLines.last)
      return TextRange(startOffset, endOffset)
    }

    fun getContentText(editor: Editor): String {
      val document = editor.document
      return getContentText(document).toString()
    }

    fun getContentText(document: Document): CharSequence {
      val first = firstContentLine
      val last = lastContentLine
      val charsSequence = document.charsSequence
      return if (first <= last) {
        charsSequence.subSequence(document.getLineStartOffset(first), document.getLineEndOffset(last))
      }
      else {
        ""
      }
    }

    fun getTopMarker(document: Document): String? =
      if (markers.hasTopLine) document.getLineText(lines.first) else null

    fun getBottomMarker(document: Document): String? =
      if (markers.hasBottomLine) document.getLineText(lines.last) else null
  }

  interface IntervalListener : EventListener {
    /**
     * Called each time when document is changed, even if intervals are the same.
     * Contains DocumentEvent and additional information about intervals.
     * Components which work with intervals can simply listen for NotebookCellLinesEvent and don't subscribe for DocumentEvent.
     * Listener shouldn't throw exceptions
     */
    fun documentChanged(event: NotebookCellLinesEvent)

    /**
     * Called each time before document is changed.
     * Listener shouldn't throw exceptions
     */
    fun beforeDocumentChange(event: NotebookCellLinesEventBeforeChange) {}
    fun bulkUpdateFinished() {}
  }

  fun intervalsIterator(startLine: Int = 0): ListIterator<Interval>

  fun getCell(line: Int) = intervals.firstOrNull { it.lines.contains(line) }

  val intervals: List<Interval>

  val intervalListeners: EventDispatcher<IntervalListener>

  val modificationStamp: Long

  companion object {
    fun get(document: Document): NotebookCellLines =
      NotebookCellLinesProvider.get(document)?.create(document)
      ?: error("Can't get NotebookCellLinesProvider for document ${document}")

    fun hasSupport(document: Document): Boolean =
      NotebookCellLinesProvider.get(document) != null

    fun get(editor: Editor): NotebookCellLines =
      get(editor.document)

    fun hasSupport(editor: Editor): Boolean =
      hasSupport(editor.document)

    val INTERVAL_LANGUAGE_KEY = Key.create<Language>("com.intellij.notebooks.visualization.NotebookCellLines.Interval.language")
  }
}