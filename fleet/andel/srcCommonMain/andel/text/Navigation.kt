// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import andel.editor.Caret
import andel.editor.CaretPosition
import andel.editor.MutableDocument
import fleet.codepoints.CodepointClass
import fleet.codepoints.codePointAt
import fleet.codepoints.codePointBefore
import fleet.codepoints.codepointClass
import fleet.codepoints.forEachCodepoint
import fleet.codepoints.forEachCodepointReversed

private enum class Direction {
  FORWARD,
  BACKWARD,
}

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
  var classBefore = CodepointClass.CARET
  var charOffset = offset
  text.charSequence(offset, text.charCount.toLong()).forEachCodepoint { codepoint ->
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
  var classBefore = CodepointClass.CARET
  var charOffset = offset
  text.charSequence(0, offset).forEachCodepointReversed { codepoint ->
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
  val rightCodepoint = if (offset in 0..<text.charCount) {
    text.charSequence().codePointAt(offset.toInt()).let { cursor ->
      codepointClass(cursor.codepoint)
    }
  }
  else {
    null
  }
  val leftCodepoint = if (offset in 1..text.charCount) {
    text.charSequence().codePointBefore(offset.toInt()).let { cursor ->
      codepointClass(cursor.codepoint)
    }
  }
  else {
    null
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
