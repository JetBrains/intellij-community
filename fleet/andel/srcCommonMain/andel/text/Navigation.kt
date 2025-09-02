// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import andel.editor.Caret
import andel.editor.CaretPosition
import andel.editor.MutableDocument
import fleet.util.text.CodepointClass
import fleet.util.text.Direction
import fleet.util.text.codepointClass
import fleet.util.text.codepoints

private fun stopOnCodepointClass(
  before: CodepointClass,
  after: CodepointClass,
  direction: Direction,
  honorCamelHumps: Boolean,
  stopAfterSpace: Boolean,
): Boolean {
  val upDownSwitchCase = before == CodepointClass.UPPERCASE && after == CodepointClass.LOWERCASE
  val downUpSwitchCase = before == CodepointClass.LOWERCASE && after == CodepointClass.UPPERCASE
  val underscoreIsPrev = before == CodepointClass.UNDERSCORE && after != CodepointClass.UNDERSCORE
  val underscoreIsNext = before != CodepointClass.UNDERSCORE && after == CodepointClass.UNDERSCORE
  return when {
    before == CodepointClass.CARET -> false
    before == after -> false
    before == CodepointClass.SPACE -> stopAfterSpace
    before == CodepointClass.SEPARATOR -> true
    after == CodepointClass.SEPARATOR -> true
    after == CodepointClass.NEWLINE -> true
    after == CodepointClass.SPACE -> true
    underscoreIsPrev || underscoreIsNext -> honorCamelHumps
    downUpSwitchCase -> direction == Direction.FORWARD && honorCamelHumps
    upDownSwitchCase -> direction == Direction.BACKWARD && honorCamelHumps
    else -> true
  }
}

fun textRight(
  offset: Long,
  text: TextView,
  range: TextRange,
  honorCamelHumps: Boolean,
  stopAfterSpace: Boolean,
): Long {
  if (offset == range.end) return offset
  val cursor = text.charSequence().codepoints(offset.toInt(), Direction.FORWARD)
  var classBefore = CodepointClass.CARET
  var charOffset = offset
  for (codepoint in cursor) {
    val classAfter = codepointClass(codepoint.codepoint)
    if (stopOnCodepointClass(classBefore, classAfter, Direction.FORWARD, honorCamelHumps, stopAfterSpace) ||
        charOffset >= range.end) {
      return charOffset
    }
    charOffset += codepoint.charCount
    classBefore = classAfter
  }
  return charOffset
}

fun textRight(offset: Long, text: TextView, honorCamelHumps: Boolean, stopAfterSpace: Boolean): Long {
  return textRight(offset, text, TextRange(0, text.charCount.toLong()), honorCamelHumps, stopAfterSpace)
}

private fun textLeft(
  offset: Long,
  text: TextView,
  range: TextRange,
  honorCamelHumps: Boolean,
  stopAfterSpace: Boolean,
): Long {
  if (offset == range.start) return offset
  val cursor = text.charSequence().codepoints(offset.toInt(), Direction.BACKWARD)
  var classBefore = CodepointClass.CARET
  var charOffset = offset
  for (codepoint in cursor) {
    val classAfter = codepointClass(codepoint.codepoint)
    if (stopOnCodepointClass(classBefore, classAfter, Direction.BACKWARD, honorCamelHumps, stopAfterSpace)) {
      return charOffset
    }
    charOffset -= codepoint.charCount
    classBefore = classAfter
  }
  return charOffset
}

fun textLeft(offset: Long, text: TextView, honorCamelHumps: Boolean, stopAfterSpace: Boolean): Long {
  return textLeft(offset, text, TextRange(0, text.charCount.toLong()), honorCamelHumps, stopAfterSpace)
}

fun textAround(
  offset: Long,
  text: TextView,
  honorCamelHumps: Boolean,
  requireWordAtCaret: Boolean = true,
): TextRange {
  return textAround(offset, text, TextRange(0, text.charCount.toLong()), honorCamelHumps, requireWordAtCaret)
}

fun textAround(
  offset: Long,
  text: TextView,
  range: TextRange,
  honorCamelHumps: Boolean,
  requireWordAtCaret: Boolean = true,
): TextRange {
  val validCodepoints = setOf(CodepointClass.LOWERCASE, CodepointClass.UPPERCASE, CodepointClass.UNDERSCORE)
  val rightCodepoint = run {
    val cursor = text.charSequence().codepoints(offset.toInt(), Direction.FORWARD)
    if (cursor.hasNext()) {
      val next = cursor.next()
      codepointClass(next.codepoint)
    }
    else null
  }

  val leftCodepoint = run {
    val cursor = text.charSequence().codepoints(offset.toInt(), Direction.BACKWARD)
    if (cursor.hasNext()) {
      val prev = cursor.next()
      codepointClass(prev.codepoint)
    }
    else null
  }

  val isWordOnTheRight = rightCodepoint in validCodepoints
  val isWordOnTheLeft = leftCodepoint in validCodepoints

  if (!isWordOnTheRight && !isWordOnTheLeft && requireWordAtCaret) {
    return TextRange(offset, offset)
  }

  return if (isWordOnTheRight) {
    val end = textRight(offset, text, range, honorCamelHumps, stopAfterSpace = false)
    val start = textLeft(end, text, range, honorCamelHumps, stopAfterSpace = false)
    TextRange(start, end)
  }
  else {
    val start = textLeft(offset, text, range, honorCamelHumps, stopAfterSpace = false)
    val end = textRight(start, text, range, honorCamelHumps, stopAfterSpace = false)
    TextRange(start, end)
  }
}

fun textAround(
  caret: CaretPosition,
  document: MutableDocument,
  honorCamelHumps: Boolean,
): TextRange {
  return textAround(caret.offset, document.text.view(), honorCamelHumps)
}

fun textAroundOrNull(caret: Caret, text: TextView, honorCamelHumps: Boolean): TextRange? {
  val result = textAround(caret.offset, text, honorCamelHumps)
  return when {
    // NOTE: include both edges of range to consider a case when caret is on the right edge
    result.start < result.end && caret.offset in result.start..result.end -> result
    else -> null
  }
}
