// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import andel.operation.Operation
import andel.text.impl.TextLineImpl
import andel.text.impl.TextLinesImpl

fun TextView.charSequence(): TextFragmentCharSequence = WholeTextCharSequence(this)

fun TextView.charSequence(range: TextRange): TextFragmentCharSequence = charSequence(range.start, range.end)

fun TextView.charSequence(from: Long, to: Long): TextFragmentCharSequence = WholeTextCharSequence(this).fragment(from, to)

val TextView.lastLine: LineNumber get() = lineCount - 1.line

fun TextView.lineEndOffset(lineIndex: LineNumber, includeLineSeparator: Boolean = false): Int =
  when {
    lineIndex == lastLine -> charCount
    includeLineSeparator -> lineStartOffset(lineIndex + 1.line)
    else -> lineStartOffset(lineIndex + 1.line) - 1
  }

fun TextView.lineRange(lineNumber: LineNumber, includeLineSeparator: Boolean = false): TextRange =
  TextRange(lineStartOffset(lineNumber), lineEndOffset(lineNumber, includeLineSeparator))

fun TextView.string(range: TextRange): String =
  string(range.start.toInt(), range.end.toInt())

fun TextView.textLines(includeSeparator: Boolean = false): TextLines =
  TextLinesImpl(this, includeSeparator)

fun TextView.textLine(lineNumber: LineNumber, includeSeparator: Boolean = false): TextLine =
  TextLineImpl(
    lineNumber = lineNumber.line.toLong(),
    fromChar = lineStartOffset(lineNumber).toLong(),
    toChar = lineEndOffset(lineNumber, includeLineSeparator = includeSeparator).toLong(),
    includesSeparator = includeSeparator && lineNumber != lastLine,
    tryIncludeSeparator = includeSeparator,
    textView = this
  )

fun TextView.textLineAtOffset(offset: Int, includeSeparator: Boolean = false): TextLine =
  textLine(lineAt(offset), includeSeparator)

fun TextView.deleteOperation(from: Int, to: Int): Operation =
  Operation.deleteAt(from.toLong(), string(from, to), charCount.toLong())

fun TextView.replaceOperation(from: Int, to: Int, replacement: String, deduce: Boolean = false): Operation =
  Operation.replaceAt(from.toLong(), string(from, to), replacement, charCount.toLong(), deduce)

fun TextView.insertOperation(offset: Int, s: String): Operation =
  Operation.insertAt(offset.toLong(), s, charCount.toLong())