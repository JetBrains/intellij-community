// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AnalysisIgnoreLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var bufferEnd: Int = 0
  private var tokenStart: Int = 0
  private var tokenEnd: Int = 0
  private var tokenType: IElementType? = null

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    bufferEnd = endOffset
    tokenStart = startOffset
    locateToken()
  }

  override fun getState(): Int = 0

  override fun getTokenType(): IElementType? = tokenType

  override fun getTokenStart(): Int = tokenStart

  override fun getTokenEnd(): Int = tokenEnd

  override fun advance() {
    tokenStart = tokenEnd
    locateToken()
  }

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = bufferEnd

  private fun locateToken() {
    if (tokenStart >= bufferEnd) {
      tokenEnd = tokenStart
      tokenType = null
      return
    }

    val lineEnd = lineEnd(tokenStart)
    if (buffer[tokenStart] == '#' && startsLine(tokenStart)) {
      tokenEnd = lineEnd
      tokenType = AnalysisIgnoreElementTypes.COMMENT
      return
    }

    val patternEnd = contentEnd(tokenStart, lineEnd)
    if (patternEnd > tokenStart) {
      tokenEnd = patternEnd
      tokenType = AnalysisIgnoreElementTypes.PATTERN
      return
    }

    tokenEnd = whiteSpaceEnd(lineEnd)
    tokenType = TokenType.WHITE_SPACE
  }

  private fun startsLine(offset: Int): Boolean = offset == 0 || isLineBreak(buffer[offset - 1])

  /** Returns the offset of the line break that ends the line at [start], or the end of the buffer. */
  private fun lineEnd(start: Int): Int {
    var end = start
    while (end < bufferEnd && !isLineBreak(buffer[end])) end++
    return end
  }

  /** Returns the end of the content in `[start, lineEnd)`, without the trailing spaces. Returns [start] for spaces only. */
  private fun contentEnd(start: Int, lineEnd: Int): Int {
    var end = lineEnd
    while (end > start && buffer[end - 1] == ' ') end--
    return end
  }

  private fun whiteSpaceEnd(start: Int): Int {
    var end = start
    while (end < bufferEnd) {
      if (isLineBreak(buffer[end])) {
        end++
        continue
      }
      val nextLineEnd = lineEnd(end)
      if (contentEnd(end, nextLineEnd) > end) break
      end = nextLineEnd
    }
    return end
  }
}

private fun isLineBreak(c: Char): Boolean = c == '\n' || c == '\r'
