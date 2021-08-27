package org.jetbrains.plugins.notebooks.editor

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.Document
import com.intellij.psi.tree.IElementType
import kotlin.math.max

interface NotebookCellLinesLexer {
  fun shouldParseWholeFile(): Boolean = false

  fun markerSequence(chars: CharSequence, ordinalIncrement: Int, offsetIncrement: Int): Sequence<NotebookCellLines.Marker>

  companion object {
    fun defaultMarkerSequence(underlyingLexerFactory: () -> Lexer,
                              tokenToCellType: (IElementType) -> NotebookCellLines.CellType?,
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

    fun defaultIntervals(document: Document, markers: List<NotebookCellLines.Marker>): List<NotebookCellLines.Interval> {
      val lineMarkers = toLineMarkers(document, markers)

      val result = mutableListOf<NotebookCellLines.Interval>()
      for (i in 0 until (lineMarkers.size - 1)) {
        result += NotebookCellLines.Interval(ordinal = i, type = lineMarkers[i].type,
          lines = lineMarkers[i].startLine until lineMarkers[i + 1].startLine)
      }
      return result
    }
  }
}

private fun toLineMarkers(document: Document, markers: List<NotebookCellLines.Marker>): List<LineMarker> {
  val m = mutableListOf<LineMarker>()

  // add first if necessary
  if (markers.isEmpty() || document.getLineNumber(markers.first().offset) != 0) {
    m += LineMarker(0, NotebookCellLines.CellType.RAW)
  }

  for (marker in markers) {
    m += LineMarker(document.getLineNumber(marker.offset), marker.type)
  }

  // marker for the end
  m += LineMarker(max(document.lineCount, 1), NotebookCellLines.CellType.RAW)
  return m
}

private data class LineMarker(val startLine: Int, val type: NotebookCellLines.CellType)