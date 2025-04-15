// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text.impl

import andel.operation.Operation
import andel.rope.Metric
import andel.text.LineNumber
import andel.text.MutableTextView
import andel.text.Text
import andel.text.line

internal class TextViewImpl(private var textRope: TextRope) : MutableTextView {

  override var charCount: Int = textRope.charCount

  override var lineCount: LineNumber = LineNumber(textRope.linesCount)

  private var cursor: CachedCursor = textRope.cursor(Any()).cached()

  private fun checkOffsetBounds(offset: Int) {
    require(0 <= offset) { "negative offset $offset" }
    require(offset <= charCount) { "offset out of bounds: $charCount" }
  }

  override fun edit(operation: Operation) {
    val newRope = textRope.edit(operation)
    textRope = newRope
    charCount = newRope.charCount
    lineCount = newRope.linesCount.line
    cursor = newRope.cursor(Any()).cached()
  }

  private fun moveToLine(editor: Any?, line: Int): CachedCursor =
    cursor.let { z ->
      when {
        z.fromLine <= line && line < z.toLine -> z
        else -> {
          moveImpl(z.cursor, editor, TextMonoid.NewlinesCount, line).cached().also {
            cursor = it
          }
        }
      }
    }
  
  private fun moveToChar(editor: Any?, offset: Int): CachedCursor =
    cursor.let { z ->
      when {
        z.from <= offset && offset < z.to -> z
        else -> {
          checkOffsetBounds(offset)
          moveImpl(z.cursor, editor, TextMonoid.CharsCount, offset).cached().also {
            cursor = it
          }
        }
      }
    }

  private fun moveImpl(z: TextCursor, editor: Any?, metricsKind: Metric, offset: Int): TextCursor =
    z.scan(editor ?: Any(), metricsKind, offset)

  override fun text(): Text = Text(textRope)

  override fun get(offset: Int): Char {
    val z = moveToChar(null, offset)
    val nodeOffset = z.from
    val leafOffset = offset - nodeOffset
    val leaf = z.data
    return leaf[leafOffset]
  }

  override fun string(from: Int, to: Int): String {
    checkOffsetBounds(from)
    require(to <= charCount) {
      "to: $to is out of bounds $charCount"
    }
    require(from <= to) {
      "from: $from to: $to"
    }
    if (from == to) return ""
    val editor = Any()
    val z = moveToChar(editor, from)
    return buildString(to - from) {
      val zPrime = z.cursor.substring(editor, from, to, this::append).cached()
      cursor = zPrime
    }
  }

  override fun lineAt(offset: Int): LineNumber {
    checkOffsetBounds(offset)
    val z = moveToChar(null, offset)
    val leafLine = z.cursor.location(TextMonoid.NewlinesCount)
    val leafOffset = z.from
    val offsetInLeaf = offset - leafOffset
    var newlinesInLeafBeforeOffset = 0
    if (offsetInLeaf > 0) {
      val leaf = z.data
      for (i in 0 until offsetInLeaf) {
        if (leaf[i] == '\n') {
          newlinesInLeafBeforeOffset++
        }
      }
    }
    return (leafLine + newlinesInLeafBeforeOffset).line
  }

  override fun lineStartOffset(lineNumber: LineNumber): Int {
    require(0.line <= lineNumber) { "negative line number $lineNumber" }
    require(lineNumber < lineCount) { "line $lineNumber is out of bounds: $lineCount" }
    if (lineNumber == 0.line) return 0
    val z = moveToLine(null, lineNumber.line - 1)
    val leafLine = z.cursor.location(TextMonoid.NewlinesCount)
    val leafOffset = z.from
    var i = 0
    var newlinesInLeaf = 0
    val lineIndexInLeaf = lineNumber.line - leafLine
    if (newlinesInLeaf < lineIndexInLeaf) {
      val leaf = z.data
      while (newlinesInLeaf < lineIndexInLeaf) {
        if (leaf[i] == '\n') {
          newlinesInLeaf++
        }
        i++
      }
    }
    return leafOffset + i
  }
}

private data class CachedCursor(
  val from: Int,
  val to: Int,
  val fromLine: Int,
  val toLine: Int,
  val data: String,
  val cursor: TextCursor,
)

private fun TextCursor.cached(): CachedCursor {
  val offset = location(TextMonoid.CharsCount)
  val line = location(TextMonoid.NewlinesCount)
  val lineCount = size(TextMonoid.NewlinesCount)
  val length = size(TextMonoid.CharsCount)
  return CachedCursor(
    cursor = this,
    from = offset,
    to = offset + length,
    fromLine = line,
    toLine = line + lineCount,
    data = element
  )
}

