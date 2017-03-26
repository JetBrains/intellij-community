/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.util

import com.intellij.diff.DiffTestCase
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.openapi.editor.impl.DocumentImpl

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
    val lineOffsets = LineOffsets.create(text)
    val document = DocumentImpl(text)

    assertEquals(lineOffsets.lineCount, getLineCount(document))
    assertEquals(lineOffsets.textLength, document.textLength)

    for (i in 0 until lineOffsets.lineCount) {
      assertEquals(lineOffsets.getLineStart(i), document.getLineStartOffset(i))
      assertEquals(lineOffsets.getLineEnd(i), document.getLineEndOffset(i))
    }
  }

  private fun checkOffsets(text: String, vararg offsets: IntPair) {
    val lineOffsets = LineOffsets.create(text)

    assertEquals(offsets.size, lineOffsets.lineCount)

    offsets.forEachIndexed { i, value ->
      assertEquals(lineOffsets.getLineStart(i), value.val1)
      assertEquals(lineOffsets.getLineEnd(i), value.val2)
    }
  }

  private operator fun Int.not(): IntPairHelper = IntPairHelper(this)
  private operator fun IntPairHelper.minus(col: Int): IntPair = IntPair(this.line, col)
  private class IntPairHelper(val line: Int)
}