package org.jetbrains.plugins.notebooks.editor

import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType

interface NotebookCellLinesLexer {
  fun shouldParseWholeFile(): Boolean = false

  fun markerSequence(chars: CharSequence, ordinalIncrement: Int, offsetIncrement: Int): Sequence<NotebookCellLines.Marker>

  companion object {
    fun defaultMarkerSequence(underlyingLexerFactory: () -> Lexer,
                              tokenToCellType: (IElementType) -> NotebookCellLines.CellType?,
                              chars: CharSequence,
                              ordinalIncrement: Int,
                              offsetIncrement: Int): Sequence<NotebookCellLines.Marker> = sequence{
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
  }
}