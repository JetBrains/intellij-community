package org.jetbrains.plugins.notebooks.visualization

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.Document
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines.CellType
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines.MarkersAtLines
import kotlin.math.max

interface NotebookCellLinesLexer {
  fun shouldParseWholeFile(): Boolean = false

  fun markerSequence(chars: CharSequence, ordinalIncrement: Int, offsetIncrement: Int): Sequence<NotebookCellLines.Marker>

  companion object {
    fun defaultMarkerSequence(underlyingLexerFactory: () -> Lexer,
                              tokenToCellType: (IElementType) -> CellType?,
                              chars: CharSequence,
                              ordinalIncrement: Int,
                              offsetIncrement: Int): Sequence<NotebookCellLines.Marker> = sequence {
      val lexer = underlyingLexerFactory()
      lexer.start(chars, 0, chars.length)
      var ordinal = 0
      while (true) {
        val tokenType = lexer.tokenType ?: break
        val cellType = tokenToCellType(tokenType)
        if (cellType != null) {
          yield(NotebookCellLines.Marker(
            ordinal = ordinal++ + ordinalIncrement,
            type = cellType,
            offset = lexer.currentPosition.offset + offsetIncrement,
            length = lexer.tokenText.length,
          ))
        }
        lexer.advance()
      }
    }

    private fun defaultIntervals(document: Document, markers: List<NotebookCellLines.Marker>): List<NotebookCellLines.Interval> {
      val intervals = toIntervalsInfo(document, markers)

      val result = mutableListOf<NotebookCellLines.Interval>()
      for (i in 0 until (intervals.size - 1)) {
        result += NotebookCellLines.Interval(ordinal = i, type = intervals[i].second,
          lines = intervals[i].first until intervals[i + 1].first, markers = intervals[i].third)
      }
      return result
    }

    fun intervalsGeneratorFromLexer(lexer: NotebookCellLinesLexer): (Document) -> List<NotebookCellLines.Interval> = { document ->
      val markers = lexer.markerSequence(document.charsSequence, 0, 0).toList()
      defaultIntervals(document, markers)
    }
  }
}

private fun toIntervalsInfo(document: Document, markers: List<NotebookCellLines.Marker>): List<Triple<Int, CellType, MarkersAtLines>> {
  val m = mutableListOf<Triple<Int, CellType, MarkersAtLines>>()

  // add first if necessary
  if (markers.isEmpty() || document.getLineNumber(markers.first().offset) != 0) {
    m += Triple(0, CellType.RAW, MarkersAtLines.NO)
  }

  for (marker in markers) {
    m += Triple(document.getLineNumber(marker.offset), marker.type, MarkersAtLines.TOP)
  }

  // marker for the end
  m += Triple(max(document.lineCount, 1), CellType.RAW, MarkersAtLines.NO)
  return m
}