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

import com.intellij.diff.comparison.AutoTestCase
import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.HashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class UnifiedFragmentBuilderAutoTest : AutoTestCase() {
  public fun test() {
    doTest(System.currentTimeMillis(), 30, 30)
  }

  public fun doTest(seed: Long, runs: Int, maxLength: Int) {
    RNG.setSeed(seed)

    var policy: ComparisonPolicy? = null
    var masterSide: Side? = null
    var lastSeed: Long = -1;

    for (i in 1..runs) {
      if (i % 1000 == 0) println(i)
      var text1: Document? = null
      var text2: Document? = null
      try {
        lastSeed = getCurrentSeed()

        text1 = generateText(maxLength)
        text2 = generateText(maxLength)

        for (side in Side.values()) {
          for (comparisonPolicy in ComparisonPolicy.values()) {
            policy = comparisonPolicy
            masterSide = side
            doTest(text1, text2, policy, masterSide)
          }
        }
      }
      catch (e: Throwable) {
        println("Seed: " + seed)
        println("Runs: " + runs)
        println("MaxLength: " + maxLength)
        println("Policy: " + policy!!)
        println("Current side: " + masterSide!!)
        println("I: " + i)
        println("Current seed: " + lastSeed)
        println("Text1: " + textToReadableFormat(text1?.getCharsSequence()))
        println("Text2: " + textToReadableFormat(text2?.getCharsSequence()))
        throw e
      }
    }
  }

  public fun doTest(document1: Document, document2: Document, policy: ComparisonPolicy, masterSide: Side) {
    val sequence1 = document1.getCharsSequence()
    val sequence2 = document2.getCharsSequence()

    val fragments = MANAGER.compareLinesInner(sequence1, sequence2, policy, DumbProgressIndicator.INSTANCE)

    val builder = UnifiedFragmentBuilder(fragments, document1, document2, masterSide)
    builder.exec()

    val ignoreWhitespaces = policy !== ComparisonPolicy.DEFAULT
    val text = builder.getText()
    val blocks = builder.getBlocks()
    val convertor = builder.getConvertor()
    val changedLines = builder.getChangedLines()
    val ranges = builder.getRanges()

    // both documents - before and after - should be subsequence of result text.
    assertTrue(isSubsequence(text, sequence1, ignoreWhitespaces))
    assertTrue(isSubsequence(text, sequence2, ignoreWhitespaces))

    // all changes should be inside ChangedLines
    for (fragment in fragments) {
      val startLine1 = fragment.getStartLine1()
      val endLine1 = fragment.getEndLine1()
      val startLine2 = fragment.getStartLine2()
      val endLine2 = fragment.getEndLine2()

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
    assertEquals(blocks.size(), fragments.size())
    for (i in fragments.indices) {
      val fragment = fragments.get(i)
      val block = blocks.get(i)

      val fragment1 = sequence1.subSequence(fragment.getStartOffset1(), fragment.getEndOffset1())
      val fragment2 = sequence2.subSequence(fragment.getStartOffset2(), fragment.getEndOffset2())

      val block1 = text.subSequence(block.getStartOffset1(), block.getEndOffset1())
      val block2 = text.subSequence(block.getStartOffset2(), block.getEndOffset2())

      assertEqualsCharSequences(fragment1, block1, ignoreWhitespaces, true)
      assertEqualsCharSequences(fragment2, block2, ignoreWhitespaces, true)
    }

    // ranges should have exact same content
    for (range in ranges) {
      val sideSequence = range.getSide().select(sequence1, sequence2)
      val baseRange = text.subSequence(range.getBase().getStartOffset(), range.getBase().getEndOffset())
      val sideRange = sideSequence.subSequence(range.getChanged().getStartOffset(), range.getChanged().getEndOffset())
      assertTrue(StringUtil.equals(baseRange, sideRange))
    }
  }

  private fun isSubsequence(text: CharSequence, sequence: CharSequence, ignoreWhitespaces: Boolean): Boolean {
    var index1 = 0
    var index2 = 0

    while (index2 < sequence.length()) {
      val c2 = sequence.charAt(index2)
      if (c2 == '\n' || (StringUtil.isWhiteSpace(c2) && ignoreWhitespaces)) {
        index2++
        continue
      }

      assertTrue(index1 < text.length())
      val c1 = text.charAt(index1)
      if (c1 == '\n' || (StringUtil.isWhiteSpace(c1) && ignoreWhitespaces)) {
        index1++
        continue
      }

      if (c1 == c2) {
        index1++
        index2++
      }
      else {
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

  private fun generateText(maxLength: Int): Document {
    return DocumentImpl(generateText(maxLength, CHAR_COUNT, CHAR_TABLE))
  }

  companion object {
    private val MANAGER = ComparisonManagerImpl()

    private val CHAR_COUNT = 12
    private val CHAR_TABLE: Map<Int, Char> = {
      val map = HashMap<Int, Char>()
      listOf('\n', '\n', '\t', ' ', ' ', '.', '<', '!').forEachIndexed { i, c -> map.put(i, c) }
      map
    }()
  }
}
