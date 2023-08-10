package org.jetbrains.plugins.notebooks.visualization

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines.CellType
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines.MarkersAtLines
import kotlin.math.max

interface NotebookCellLinesLexer {
  fun markerSequence(chars: CharSequence, ordinalIncrement: Int, offsetIncrement: Int, defaultLanguage: Language): Sequence<Marker>

  data class Marker(
    val ordinal: Int,
    val type: CellType,
    val offset: Int,
    val length: Int,
    var language: Language? = null
  ) : Comparable<Marker> {
    override fun compareTo(other: Marker): Int = offset - other.offset
  }

  companion object {
    fun defaultMarkerSequence(underlyingLexerFactory: () -> Lexer,
                              tokenToCellType: (IElementType) -> CellType?,
                              chars: CharSequence,
                              ordinalIncrement: Int,
                              offsetIncrement: Int): Sequence<Marker> = sequence {
      val lexer = underlyingLexerFactory()
      lexer.start(chars, 0, chars.length)
      var ordinal = 0
      while (true) {
        val tokenType = lexer.tokenType ?: break
        val cellType = tokenToCellType(tokenType)

        if (cellType != null) {
          yield(Marker(
            ordinal = ordinal++ + ordinalIncrement,
            type = cellType,
            offset = lexer.currentPosition.offset + offsetIncrement,
            length = lexer.tokenText.length,
          ))
        }
        lexer.advance()
      }
    }

    fun defaultIntervals(document: Document, markers: List<Marker>): List<NotebookCellLines.Interval> {
      val intervals = toIntervalsInfo(document, markers)

      val result = mutableListOf<NotebookCellLines.Interval>()
      for (i in 0 until (intervals.size - 1)) {
        result += NotebookCellLines.Interval(ordinal = i, type = intervals[i].cellType,
                                             lines = intervals[i].lineNumber until intervals[i + 1].lineNumber,
                                             markers = intervals[i].markersAtLInes,
                                             intervals[i].language)
      }
      return result
    }

  }
}

private data class IntervalInfo(val lineNumber: Int, val cellType: CellType, val markersAtLInes: MarkersAtLines, val language: Language)

private fun toIntervalsInfo(document: Document,
                            markers: List<NotebookCellLinesLexer.Marker>): List<IntervalInfo> {
  val m = mutableListOf<IntervalInfo>()

  // add first if necessary
  if (markers.isEmpty() || document.getLineNumber(markers.first().offset) != 0) {
    m += IntervalInfo(0, CellType.RAW, MarkersAtLines.NO, PlainTextLanguage.INSTANCE)
  }

  for (marker in markers) {
    m += IntervalInfo(document.getLineNumber(marker.offset), marker.type, MarkersAtLines.TOP, marker.language!!) // marker.language is provided in makeIntervals
  }

  // marker for the end
  m += IntervalInfo(max(document.lineCount, 1), CellType.RAW, MarkersAtLines.NO, PlainTextLanguage.INSTANCE)
  return m
}