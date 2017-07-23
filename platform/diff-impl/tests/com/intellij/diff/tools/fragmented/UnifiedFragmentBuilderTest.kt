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
import com.intellij.diff.comparison.iterables.DiffIterable
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.DocumentImpl
import junit.framework.TestCase
import java.util.*

class UnifiedFragmentBuilderTest : DiffTestCase() {
  fun testSimple() {
    Test("", "",
         ".",
         " ")
      .run()

    Test("A_B_C", "A_B_C",
         "A_B_C",
         " _ _ ")
      .run()

    // empty document has one line, so it's "modified" case (and not "deleted")
    Test("A", "",
         "A_.",
         "L_R")
      .run()

    Test("", "A",
         "._A",
         "L_R")
      .run()

    Test("A_", "",
         "A_.",
         "L_ ")
      .run()

    Test("A_", "B",
         "A_._B",
         "L_L_R")
      .run()

    Test("B_", "B",
         "B_.",
         " _L")
      .run()

    Test("_B", "B",
         "._B",
         "L_ ")
      .run()

    Test("A_A", "B",
         "A_A_B",
         "L_L_R")
      .run()

    Test("A_B_C_D", "A_D",
         "A_B_C_D",
         " _L_L_ ")
      .run()

    Test("X_B_C", "A_B_C",
         "X_A_B_C",
         "L_R_ _ ")
      .run()

    Test("A_B_C", "A_B_Y",
         "A_B_C_Y",
         " _ _L_R")
      .run()

    Test("A_B_C_D_E_F", "A_X_C_D_Y_F",
         "A_B_X_C_D_E_Y_F",
         " _L_R_ _ _L_R_ ")
      .run()

    Test("A_B_C_D_E_F", "A_B_X_C_D_F",
         "A_B_X_C_D_E_F",
         " _ _R_ _ _L_ ")
      .run()

    Test("A_B_C_D_E_F", "A_B_X_Y_E_F",
         "A_B_C_D_X_Y_E_F",
         " _ _L_L_R_R_ _ ")
      .run()

    Test("", "",
         ".",
         " ")
      .changes()
      .run()

    Test("A", "B",
         "A_B",
         "L_R")
      .changes(mod(0, 0, 1, 1))
      .runLeft()

    Test("A", "B",
         "A",
         " ")
      .changes()
      .runLeft()

    Test("A", "B",
         "B",
         " ")
      .changes()
      .runRight()
  }

  fun testNonFair() {
    Test("A_B", "",
         "A_B",
         " _ ")
      .changes()
      .runLeft()

    Test("A_B", "",
         ".",
         " ")
      .changes()
      .runRight()

    Test("A_B", "A_._B",
         "A_B",
         " _ ")
      .changes()
      .runLeft()

    Test("A_B", "A_._B",
         "A_._B",
         " _ _ ")
      .changes()
      .runRight()

    Test("_._A_._", "X",
         "._._A_X_._.",
         " _ _L_R_ _ ")
      .changes(mod(2, 0, 1, 1))
      .runLeft()

    Test("_._A_._", "X",
         "A_X",
         "L_R")
      .changes(mod(2, 0, 1, 1))
      .runRight()

    Test("A_B_C_D", "X_BC_Y",
         "A_X_BC_D_Y",
         "L_R_  _L_R")
      .changes(mod(0, 0, 1, 1), mod(3, 2, 1, 1))
      .runRight()

    Test("A_B_C_D", "A_BC_Y",
         "A_BC_D_Y",
         " _  _L_R")
      .changes(mod(3, 2, 1, 1))
      .runRight()

    Test("AB_C_DE", "A_B_D_E",
         "AB_C_DE",
         "  _L_  ")
      .changes(del(1, 2, 1))
      .runLeft()

    Test("AB_C_DE", "A_B_D_E",
         "A_B_C_D_E",
         " _ _L_ _ ")
      .changes(del(1, 2, 1))
      .runRight()

    Test("AB_DE", "A_B_C_D_E",
         "AB_C_DE",
         "  _R_  ")
      .changes(ins(1, 2, 1))
      .runLeft()

    Test("AB_DE", "A_B_C_D_E",
         "A_B_C_D_E",
         " _ _R_ _ ")
      .changes(ins(1, 2, 1))
      .runRight()
  }

  private inner class Test(val input1: String, val input2: String,
                           val result: String,
                           val lineMapping: String) {
    private var customFragments: List<LineFragment>? = null

    fun runLeft() {
      doRun(Side.LEFT)
    }

    fun runRight() {
      doRun(Side.RIGHT)
    }

    fun run() {
      doRun(Side.LEFT, Side.RIGHT)
    }

    private fun doRun(vararg sides: Side) {
      sides.forEach { side ->
        assert(result.length == lineMapping.length)

        val text1 = processText(input1)
        val text2 = processText(input2)

        val fragments = if (customFragments != null) customFragments!!
        else MANAGER.compareLines(text1, text2, ComparisonPolicy.DEFAULT, INDICATOR)

        val builder = UnifiedFragmentBuilder(fragments, DocumentImpl(text1), DocumentImpl(text2), side)
        builder.exec()


        val lineCount1 = input1.count({ it == '_' }) + 1
        val lineCount2 = input2.count({ it == '_' }) + 1
        val resultLineCount = result.count({ it == '_' }) + 1
        val lineIterable = DiffIterableUtil.create(fragments.map { Range(it.startLine1, it.endLine1, it.startLine2, it.endLine2) },
                                                   lineCount1, lineCount2)

        val expectedText = processText(result)
        val actualText = processActualText(builder)

        val expectedMapping = processExpectedLineMapping(lineMapping)
        val actualMapping = processActualLineMapping(builder.blocks, resultLineCount)

        val expectedChangedLines = processExpectedChangedLines(lineMapping)
        val actualChangedLines = processActualChangedLines(builder.changedLines)

        val expectedMappedLines1 = processExpectedMappedLines(lineIterable, Side.LEFT)
        val expectedMappedLines2 = processExpectedMappedLines(lineIterable, Side.RIGHT)
        val actualMappedLines1 = processActualMappedLines(builder.convertor1, resultLineCount)
        val actualMappedLines2 = processActualMappedLines(builder.convertor2, resultLineCount)

        assertEquals(expectedText, actualText)
        assertEquals(expectedMapping, actualMapping)
        assertEquals(expectedChangedLines, actualChangedLines)

        if (customFragments == null) {
          assertEquals(expectedMappedLines1, actualMappedLines1)
          assertEquals(expectedMappedLines2, actualMappedLines2)
        }
      }
    }

    fun changes(vararg ranges: Range): Test {
      customFragments = ranges.map {
        LineFragmentImpl(it.start1, it.end1, it.start2, it.end2,
                         -1, -1, -1, -1)
      }
      return this
    }

    private fun processText(text: String): String {
      return text.filterNot { it == '.' }.replace('_', '\n')
    }

    private fun processActualText(builder: UnifiedFragmentBuilder): String {
      return builder.text.toString().removeSuffix("\n")
    }

    private fun processExpectedLineMapping(lineMapping: String): LineMapping {
      val leftSet = BitSet()
      val rightSet = BitSet()
      val unchangedSet = BitSet()

      lineMapping.split('_').forEachIndexed { index, line ->
        if (!line.isEmpty()) {
          val left = line.all { it == 'L' }
          val right = line.all { it == 'R' }
          val unchanged = line.all { it == ' ' }

          if (left) leftSet.set(index)
          else if (right) rightSet.set(index)
          else if (unchanged) unchangedSet.set(index)
          else TestCase.fail()
        }
      }

      return LineMapping(leftSet, rightSet, unchangedSet)
    }

    private fun processActualLineMapping(blocks: List<ChangedBlock>, lineCount: Int): LineMapping {
      val leftSet = BitSet()
      val rightSet = BitSet()
      val unchangedSet = BitSet()

      blocks.forEach {
        leftSet.set(it.range1.start, it.range1.end)
        rightSet.set(it.range2.start, it.range2.end)
      }

      unchangedSet.set(0, lineCount)
      unchangedSet.andNot(leftSet)
      unchangedSet.andNot(rightSet)

      return LineMapping(leftSet, rightSet, unchangedSet)
    }

    private fun processExpectedChangedLines(lineMapping: String): BitSet {
      val expectedMapping = processExpectedLineMapping(lineMapping)
      val result = BitSet()
      result.or(expectedMapping.left)
      result.or(expectedMapping.right)
      return result
    }

    private fun processActualChangedLines(changedRanges: List<LineRange>): BitSet {
      val result = BitSet()
      changedRanges.forEach {
        result.set(it.start, it.end)
      }
      return result
    }

    private fun processExpectedMappedLines(iterable: DiffIterable, side: Side): BitSet {
      val result = BitSet()
      DiffIterableUtil.iterateAll(iterable).forEach { pair ->
        val range = pair.first
        val start = side.select(range.start1, range.start2)
        val end = side.select(range.end1, range.end2)
        result.set(start, end)
      }
      return result
    }

    private fun processActualMappedLines(convertor: LineNumberConvertor, lineCount: Int): BitSet {
      val result = BitSet()
      for (i in -5..lineCount + 5) {
        val line = convertor.convert(i)
        if (line != -1) {
          if (result[line]) TestCase.fail()
          result.set(line)
        }
      }
      return result
    }
  }

  private fun mod(line1: Int, line2: Int, count1: Int, count2: Int): Range {
    assert(count1 != 0)
    assert(count2 != 0)
    return Range(line1, line1 + count1, line2, line2 + count2)
  }

  private fun del(line1: Int, line2: Int, count1: Int): Range {
    assert(count1 != 0)
    return Range(line1, line1 + count1, line2, line2)
  }

  private fun ins(line1: Int, line2: Int, count2: Int): Range {
    assert(count2 != 0)
    return Range(line1, line1, line2, line2 + count2)
  }

  private data class LineMapping(val left: BitSet, val right: BitSet, val unchanged: BitSet)
}
