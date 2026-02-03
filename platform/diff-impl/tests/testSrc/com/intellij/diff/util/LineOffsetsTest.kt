// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util

import com.intellij.diff.DiffTestCase
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.util.IntPair

class LineOffsetsTest : DiffTestCase() {
  fun testWithDocument() {
    checkSameAsDocument("")
    checkSameAsDocument("text")
    checkSameAsDocument("text\n")
    checkSameAsDocument("\n")
    checkSameAsDocument("text\ntext2")
    checkSameAsDocument("text\ntext2\n")
    checkSameAsDocument("\n\n\n")
  }

  fun testOffsets() {
    checkOffsets("", !0 - 0)
    checkOffsets("\n\n", !0 - 0, !1 - 1, !2 - 2)
    checkOffsets("text", !0 - 4)
    checkOffsets("text\n", !0 - 4, !5 - 5)
    checkOffsets("text\r", !0 - 5)
    checkOffsets("text\r\n", !0 - 5, !6 - 6)
  }

  private fun checkSameAsDocument(text: String) {
    val lineOffsets1 = LineOffsetsUtil.create(DocumentImpl(text))
    val lineOffsets2 = LineOffsetsUtil.create(text)

    assertEquals(lineOffsets1.lineCount, lineOffsets2.lineCount)
    assertEquals(lineOffsets1.textLength, lineOffsets2.textLength)

    for (i in 0 until lineOffsets1.lineCount) {
      assertEquals(lineOffsets1.getLineStart(i), lineOffsets2.getLineStart(i))
      assertEquals(lineOffsets1.getLineEnd(i), lineOffsets2.getLineEnd(i))
      assertEquals(lineOffsets1.getLineEnd(i, false), lineOffsets2.getLineEnd(i, false))
      assertEquals(lineOffsets1.getLineEnd(i, true), lineOffsets2.getLineEnd(i, true))
    }

    for (i in 0..lineOffsets1.textLength) {
      assertEquals(lineOffsets1.getLineNumber(i), lineOffsets2.getLineNumber(i))
    }
  }

  private fun checkOffsets(text: String, vararg offsets: IntPair) {
    val lineOffsets = LineOffsetsUtil.create(text)

    assertEquals(offsets.size, lineOffsets.lineCount)

    offsets.forEachIndexed { line, value ->
      assertEquals(lineOffsets.getLineStart(line), value.first)
      assertEquals(lineOffsets.getLineEnd(line), value.second)

      for (offset in lineOffsets.getLineStart(line)..lineOffsets.getLineEnd(line)) {
        assertEquals(line, lineOffsets.getLineNumber(offset))
      }
    }
  }

  private operator fun Int.not(): IntPairHelper = IntPairHelper(this)
  private operator fun IntPairHelper.minus(col: Int) = IntPair(this.line, col)
  private class IntPairHelper(val line: Int)
}