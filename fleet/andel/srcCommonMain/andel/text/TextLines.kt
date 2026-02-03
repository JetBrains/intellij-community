// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text


interface TextLines : List<TextLine> {
  val text: Text
  operator fun get(lineNumber: Long): TextLine
  fun atCharOffset(offset: Long): TextLine
  fun lineStartOffset(lineNumber: Long): Long
  fun lineEndOffset(lineNumber: Long): Long
  fun offsetToLineNumber(offset: Long): Long
  fun offsetToColumnPosition(offset: Long): LineColumnPosition
}

interface TextLine : TextFragment {
  val lineNumber: Long
  val toCharExcludingSeparator: Long
  val toCharIncludingSeparator: Long
  val includesSeparator: Boolean
  fun withoutSeparator(): TextLine
  fun next(): TextLine?
  fun prev(): TextLine?
  fun isFirstLine(): Boolean
  fun isLastLine(): Boolean
}

data class LineColumnPosition(val line: Long, val column: Long)
