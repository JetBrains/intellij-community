/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.fragmented

import com.intellij.diff.DiffTestCase
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.text.StringUtil

class UnifiedFragmentBuilderAutoTest : DiffTestCase() {
  fun test() {
    doTest(System.currentTimeMillis(), 30, 30)
  }

  fun doTest(seed: Long, runs: Int, maxLength: Int) {
    doAutoTest(seed, runs) { debugData ->
      debugData.put("MaxLength", maxLength)

      var text1 = DocumentImpl(generateText(maxLength))
      var text2 = DocumentImpl(generateText(maxLength))

      debugData.put("Text1", textToReadableFormat(text1.charsSequence))
      debugData.put("Text2", textToReadableFormat(text2.charsSequence))

      for (side in Side.values()) {
        for (comparisonPolicy in ComparisonPolicy.values()) {
          debugData.put("Policy", comparisonPolicy)
          debugData.put("Current side", side)
          doTest(text1, text2, comparisonPolicy, side)
        }
      }
    }
  }

  fun doTest(document1: Document, document2: Document, policy: ComparisonPolicy, masterSide: Side) {
    val sequence1 = document1.charsSequence
    val sequence2 = document2.charsSequence

    val fragments = MANAGER.compareLinesInner(sequence1, sequence2, policy, DumbProgressIndicator.INSTANCE)

    val builder = UnifiedFragmentBuilder(fragments, document1, document2, masterSide)
    builder.exec()

    val ignoreWhitespaces = policy !== ComparisonPolicy.DEFAULT
    val text = builder.text
    val blocks = builder.blocks
    val convertor = builder.convertor
    val changedLines = builder.changedLines
    val ranges = builder.ranges

    val document = DocumentImpl(text)

    // both documents - before and after - should be subsequence of result text.
    assertTrue(isSubsequence(text, sequence1, ignoreWhitespaces))
    assertTrue(isSubsequence(text, sequence2, ignoreWhitespaces))

    // all changes should be inside ChangedLines
    for (fragment in fragments) {
      val startLine1 = fragment.startLine1
      val endLine1 = fragment.endLine1
      val startLine2 = fragment.startLine2
      val endLine2 = fragment.endLine2

      for (i in startLine1 until endLine1) {
        val targetLine = convertor.convertInv1(i)
        assertTrue(targetLine != -1)
        assertTrue(isLineChanged(targetLine, changedLines))
      }
      for (i in startLine2 until endLine2) {
        val targetLine = convertor.convertInv2(i)
        assertTrue(targetLine != -1)
        assertTrue(isLineChanged(targetLine, changedLines))
      }
    }

    // changed fragments and changed blocks should have same content
    assertEquals(blocks.size, fragments.size)
    for (i in fragments.indices) {
      val fragment = fragments[i]
      val block = blocks[i]

      val fragment1 = sequence1.subSequence(fragment.startOffset1, fragment.endOffset1)
      val fragment2 = sequence2.subSequence(fragment.startOffset2, fragment.endOffset2)

      val block1 = DiffUtil.getLinesContent(document, block.range1.start, block.range1.end)
      val block2 = DiffUtil.getLinesContent(document, block.range2.start, block.range2.end)

      assertEqualsCharSequences(fragment1, block1, ignoreWhitespaces, true)
      assertEqualsCharSequences(fragment2, block2, ignoreWhitespaces, true)
    }

    // ranges should have exact same content
    for (range in ranges) {
      val sideSequence = range.side.select(sequence1, sequence2)!!
      val baseRange = text.subSequence(range.base.startOffset, range.base.endOffset)
      val sideRange = sideSequence.subSequence(range.changed.startOffset, range.changed.endOffset)
      assertTrue(StringUtil.equals(baseRange, sideRange))
    }
  }

  private fun isSubsequence(text: CharSequence, sequence: CharSequence, ignoreWhitespaces: Boolean): Boolean {
    var index1 = 0
    var index2 = 0

    while (index2 < sequence.length) {
      val c2 = sequence[index2]
      if (c2 == '\n' || (StringUtil.isWhiteSpace(c2) && ignoreWhitespaces)) {
        index2++
        continue
      }

      assertTrue(index1 < text.length)
      val c1 = text[index1]
      if (c1 == '\n' || (StringUtil.isWhiteSpace(c1) && ignoreWhitespaces)) {
        index1++
        continue
      }

      if (c1 == c2) {
        index1++
        index2++
      } else {
        index1++
      }
    }

    return true
  }

  private fun isLineChanged(line: Int, changedLines: List<LineRange>): Boolean {
    for (changedLine in changedLines) {
      if (changedLine.start <= line && changedLine.end > line) return true
    }
    return false
  }
}
