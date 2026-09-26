// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text.impl

import andel.text.LineColumnPosition
import andel.text.Text
import andel.text.TextLine
import andel.text.TextLines
import andel.text.TextView
import andel.text.line
import andel.text.lineEndOffset
import andel.text.textLine

internal class TextLinesImpl(
  val textView: TextView,
  private val includeSeparator: Boolean = false,
) : AbstractList<TextLine>(), TextLines {

  override val text: Text
    get() = textView.text()

  override val size: Int
    get() = text.lineCount.line

  override fun get(index: Int): TextLine =
    get(index.toLong())

  override operator fun get(lineNumber: Long): TextLine =
    textView.textLine(lineNumber.line, includeSeparator)

  override fun lineStartOffset(lineNumber: Long): Long =
    textView.lineStartOffset(lineNumber.line).toLong()

  override fun lineEndOffset(lineNumber: Long): Long =
    textView.lineEndOffset(lineNumber.line, includeLineSeparator = includeSeparator).toLong()

  override fun atCharOffset(offset: Long): TextLine =
    get(offsetToLineNumber(offset))

  override fun offsetToLineNumber(offset: Long): Long =
    textView.lineAt(offset.toInt()).line.toLong()

  override fun offsetToColumnPosition(offset: Long): LineColumnPosition {
    val line = offsetToLineNumber(offset)
    val lineStartOffset = lineStartOffset(line)
    val column = offset - lineStartOffset
    return LineColumnPosition(line, column)
  }
}
