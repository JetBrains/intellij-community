package com.intellij.notebooks.visualization

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.TextRange
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.keyFMap.KeyFMap
import java.awt.Graphics
import javax.swing.JComponent
import kotlin.math.max
import kotlin.reflect.KProperty

infix fun IntRange.hasIntersectionWith(other: IntRange): Boolean =
  !(first > other.last || last < other.first)

inline fun <T, G : Graphics> G.use(handler: (g: G) -> T): T =
  try {
    handler(this)
  }
  finally {
    dispose()
  }

/**
 * Creates a document listener that will be automatically unregistered when the editor is disposed.
 */
fun Editor.addEditorDocumentListener(listener: DocumentListener) {
  require(this is EditorImpl)
  if (!isDisposed) {
    document.addDocumentListener(listener, disposable)
  }
}

fun Document.getText(interval: NotebookCellLines.Interval): String =
  getText(TextRange(
    getLineStartOffset(interval.lines.first),
    getLineEndOffset(interval.lines.last)
  ))

fun Document.getLineText(line: Int): String =
  getText(TextRange(getLineStartOffset(line), getLineEndOffset(line)))

fun Editor.getCell(line: Int): NotebookCellLines.Interval =
  NotebookCellLines.get(this).intervalsIterator(line).next()

fun Editor.getCells(lines: IntRange): List<NotebookCellLines.Interval> =
  NotebookCellLines.get(this).getCells(lines).toList()

fun Editor.getCellByOrdinal(ordinal: Int): NotebookCellLines.Interval =
  NotebookCellLines.get(this).intervals[ordinal]

fun Editor.safeGetCellByOrdinal(ordinal: Int): NotebookCellLines.Interval? =
  NotebookCellLines.get(this).intervals.getOrNull(ordinal)

fun Editor.getCellByOffset(offset: Int): NotebookCellLines.Interval =
  getCell(line = document.getLineNumber(offset))

fun NotebookCellLines.getCells(lines: IntRange): Sequence<NotebookCellLines.Interval> =
  intervalsIterator(lines.first).asSequence().takeWhile { it.lines.first <= lines.last }

fun makeMarkersFromIntervals(document: Document, intervals: Iterable<NotebookCellLines.Interval>): List<NotebookCellLinesLexer.Marker> {
  val markers = ArrayList<NotebookCellLinesLexer.Marker>()

  fun addMarker(line: Int, type: NotebookCellLines.CellType, data: KeyFMap) {
    val startOffset = document.getLineStartOffset(line)
    val endOffset =
      if (line + 1 < document.lineCount) document.getLineStartOffset(line + 1)
      else document.getLineEndOffset(line)
    val length = endOffset - startOffset
    markers.add(NotebookCellLinesLexer.Marker(markers.size, type, startOffset, length, data))
  }

  for (interval in intervals) {
    if (interval.markers.hasTopLine) {
      addMarker(interval.lines.first, interval.type, interval.data)
    }
    if (interval.markers.hasBottomLine) {
      addMarker(interval.lines.last, interval.type, interval.data)
    }
  }

  return markers
}

fun groupNeighborCells(cells: List<NotebookCellLines.Interval>): List<List<NotebookCellLines.Interval>> {
  val groups = SmartList<SmartList<NotebookCellLines.Interval>>()
  for (cell in cells) {
    if (groups.lastOrNull()?.last()?.let { it.ordinal + 1 } != cell.ordinal) {
      groups.add(SmartList())
    }
    groups.last().add(cell)
  }
  return groups
}

/** Both lists should be sorted by the [IntRange.first]. */
fun MutableList<IntRange>.mergeAndJoinIntersections(other: List<IntRange>) {
  val merged = ContainerUtil.mergeSortedLists(this, other, Comparator { o1, o2 -> o1.first - o2.first }, false)
  clear()
  for (current in merged) {
    val previous = removeLastOrNull()
    when {
      previous == null -> add(current)
      previous.last + 1 >= current.first -> add(previous.first..max(previous.last, current.last))
      else -> {
        add(previous)
        add(current)
      }
    }
  }
}

fun isLineVisible(editor: EditorImpl, line: Int): Boolean {
  val lineY = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
  val viewArea = editor.scrollingModel.visibleAreaOnScrollingFinished
  return viewArea.y <= lineY && lineY <= viewArea.y + viewArea.height
}

class SwingClientProperty<T, R : T?>(name: String) {
  class Name(private val name: String) {
    override fun toString(): String = "Name($name)"
  }

  private val name = Name(name)

  operator fun getValue(thisRef: JComponent, property: KProperty<*>): R =
    @Suppress("UNCHECKED_CAST") (thisRef.getClientProperty(name) as R)

  operator fun setValue(thisRef: JComponent, property: KProperty<*>, value: R) {
    thisRef.putClientProperty(name, value)
  }
}