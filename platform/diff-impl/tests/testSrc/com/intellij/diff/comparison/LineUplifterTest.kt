// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.openapi.editor.impl.DocumentImpl

class LineUplifterTest : ComparisonUtilTestBase() {
  fun testSimpleCases() {
    word_first("", "")

    word_first("X_[Y]_Z", "X_[A_B]_Z")

    word_first("X_[Y]_Z", "X_|Z")
  }

  fun testTrickyEdgeCases() {
    word_first("_[x]", "_[x ]")

    word_first("[a\n$chGun]\n|$chGun$chGun>",
               "[b]\n[]\n$chGun$chGun\n[\n!]")

    // TODO: 3 changed blocks instead of one, first and third compensate each other
    word_first("X_|_*_Z",
               "X_[Y]__*_Z")

    word_first("|X_",
               "[Y_Y]_X_")
  }

  private fun word_first(input1: String, input2: String, policy: ComparisonPolicy = ComparisonPolicy.DEFAULT) {
    val text1 = parseUplifterSource(input1)
    val text2 = parseUplifterSource(input2)
    val expected = parseExpectedRanges(input1, input2)

    val before = DocumentImpl(text1)
    val after = DocumentImpl(text2)
    val fragments = MANAGER.compareLinesWordFirst(before.charsSequence, after.charsSequence,
                                                  LineOffsetsUtil.create(before), LineOffsetsUtil.create(after),
                                                  policy, INDICATOR)
    checkConsistency(fragments, before, after)


    checkLineRanges(fragments, expected)
  }

  private fun parseUplifterSource(string: CharSequence): String {
    return string.toString().replace('_', '\n')
      .replace("[", "")
      .replace("|", "")
      .replace("]", "")
      .replace(">", "")
  }

  private fun parseExpectedRanges(input1: CharSequence, input2: CharSequence): List<Range> {
    val lines1 = parseUplifterLineRanges(input1)
    val lines2 = parseUplifterLineRanges(input2)

    assertEquals(lines1.size, lines2.size)
    val result = mutableListOf<Range>()
    for ((lines1, lines2) in lines1.zip(lines2)) {
      result += Range(lines1.start, lines1.end, lines2.start, lines2.end)
    }

    return result
  }

  private fun parseUplifterLineRanges(string: CharSequence): List<LineRange> {
    val result = mutableListOf<LineRange>()

    var index = 0
    var line = 0

    var start = -1
    for (ch in string) {
      when (ch) {
        '[' -> {
          start = line
        }
        ']' -> {
          result += LineRange(start, line + 1)
          start = -1
        }
        '|' -> {
          result += LineRange(line, line)
          start = -1
        }
        '>' -> {
          result += LineRange(line + 1, line + 1)
          start = -1
        }
        '_', '\n' -> line++
        else -> index++
      }
    }

    return result
  }

  private fun checkLineRanges(fragments: List<LineFragment>, expected: List<Range>) {
    val ranges = fragments.map { Range(it.startLine1, it.endLine1, it.startLine2, it.endLine2) }
    assertOrderedEquals(expected, ranges)
  }
}