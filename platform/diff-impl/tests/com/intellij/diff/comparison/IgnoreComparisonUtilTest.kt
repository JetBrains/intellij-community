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
package com.intellij.diff.comparison

import com.intellij.diff.DiffTestCase
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.IntPair
import com.intellij.diff.util.Range
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.TextRange
import java.util.*

class IgnoreComparisonUtilTest : DiffTestCase() {
  fun testSimple() {
    Test("", "",
         "", "",
         "", "")
      .run()

    Test("X", "Y",
         " ", " ",
         "-", "-")
      .run()

    Test("X", "Y",
         "+", " ",
         " ", "-")
      .run()

    Test("X", "Y",
         " ", "+",
         "-", " ")
      .run()

    Test("X", "Y",
         "+", "+",
         " ", " ")
      .run()

    Test("X", "",
         " ", "",
         "-", "")
      .run()

    Test("X", "",
         "+", "",
         " ", "")
      .run()

    Test("", "Y",
         "", "+",
         "", " ")
      .run()
  }

  fun testSpaces() {
    Test("X Y Z", "A B Z",
         "++   ", "++   ",
         "  -  ", "  -  ")
      .run()

    Test("X Y Z", "A B Z",
         "   ++", "   ++",
         "---  ", "---  ")
      .run()

    Test("X Y Z", "A Y C",
         "++   ", "++   ",
         "    -", "    -")
      .run()

    Test("X Y Z", "A Y C",
         "+++++", "+++++",
         "     ", "     ")
      .run()

    Test("X Y Z", "A B C",
         "+   +", "+   +",
         "-----", "-----")
      .run()

    Test("A Y C", "A B C",
         "+   +", "+   +",
         "  -  ", "  -  ")
      .run()

    Test("A  B", "A B",
         " ++ ", " + ",
         "    ", "   ")
      .run()

    Test("A  B", "A X",
         " ++ ", " + ",
         "   -", "  -")
      .run()

    Test(" A  B", "X B ",
         "+ ++ ", " + +",
         " -   ", "-   ")
      .run()

    Test("A   B", "A B",
         "  +  ", "   ",
         " -   ", "   ")
      .run()

    Test("A   B", "A B",
         "   + ", "   ",
         " --  ", "   ")
      .run()
  }

  fun testTrim() {
    Test("A B_", "",
         "    ", "",
         "----", "")
      .run()

    Test("A B_", "",
         "++++", "",
         "    ", "")
      .run()

    Test("A B_", "",
         "   +", "",
         "----", "")
      .run()

    Test("A B_", "",
         "+++ ", "",
         "----", "")
      .run()

    Test("A B_", "",
         "+  +", "",
         "----", "")
      .run()

    Test("A B_", "",
         "+ ++", "",
         "----", "")
      .run()

    Test("A B_", "",
         " +++", "",
         "----", "")
      .run()

    Test("A_B_C_", "",
         " +++++", "",
         "--    ", "")
      .run()

    Test("A_B_C_", "",
         "++++ +", "",
         "    --", "")
      .run()
  }

  fun testLines() {
    Test("X_", "X_Y_",
         "  ", "    ",
         "  ", "  --")
      .changedLinesNumber(0, 1)
      .run()

    Test("X_", "X_Y_",
         "  ", "  ++",
         "  ", "    ")
      .run()

    Test("X_", "X_Y_",
         "  ", "++  ",
         "  ", "  --")
      .run()

    Test("X_Y_", "X_",
         "    ", "  ",
         "  --", "  ")
      .run()

    Test("X_Y_", "X_",
         "  ++", "  ",
         "    ", "  ")
      .run()

    Test("X_Y_Z", "X_B_Z",
         "     ", "     ",
         "  -  ", "  -  ")
      .changedLinesNumber(1, 1)
      .run()

    Test("X_Y_Z", "X_Y_Z",
         "     ", "     ",
         "     ", "     ")
      .run()

    Test("X_Y_Z", "X_Y_Z",
         " + + ", " + + ",
         "     ", "     ")
      .run()

    Test("X_Y_Z", "A_Y_Z",
         "     ", "     ",
         "-    ", "-    ")
      .run()

    Test("X_Y_Z", "A_Y_Z",
         "++   ", "     ",
         "     ", "-    ")
      .run()

    Test("X_Y_Z", "A_Y_Z",
         "++   ", "++   ",
         "     ", "     ")
      .run()

    Test("X_Y_Z", "A_Y_Z",
         "     ", " ++  ",
         "-    ", "-    ")
      .run()

    Test("X_Y_Z", "X_Y_C",
         "     ", "     ",
         "    -", "    -")
      .run()

    Test("X_Y_Z", "X_Y_C",
         "     ", "    +",
         "    -", "     ")
      .run()

    Test("X_Y_Z", "X_Y_C",
         "    +", "     ",
         "     ", "    -")
      .run()

    Test("X_Y_Z", "X_B_Z",
         "     ", "     ",
         "  -  ", "  -  ")
      .run()

    Test("X_Y_Z", "X_B_Z",
         "  +  ", "  +  ",
         "     ", "     ")
      .run()

    Test("X_Y_Z", "X_B_Z",
         "  +  ", "     ",
         "     ", "  -  ")
      .run()
  }

  fun testTrimLines() {
    Test("X_W_Y_W_Z", "X_B_Z",
         " +++ +++ ", " + + ",
         "    -    ", "  -  ")
      .changedLinesNumber(1, 1)
      .run()

    Test("X_W_W_Z", "X_Z",
         " +++++ ", " + ",
         "       ", "   ")
      .changedLinesNumber(0, 0)
      .run()

    Test("X_W 1 W_Y_W 2 W_Z", "X_W 3 W_B_W 4 W_Z",
         "    +       +    ", "    +       +    ",
         "        -        ", "        -        ")
      .changedLinesNumber(1, 1)
      .run()

    Test("X_W W W_Z", "X_W W_ W_Z",
         " + + + + ", " + + ++ + ",
         "         ", "          ")
      .changedLinesNumber(0, 0)
      .run()

    Test("X_W M W_Z", "X_W W_ W_Z",
         " + + + + ", " + + ++ + ",
         "    -    ", "    -     ")
      .changedLinesNumber(1, 2)
      .run()
  }

  fun testNoInnerChanges() {
    Test("X_W_Y_W_Z", "X_B_Z",
         " +++ +++ ", " + + ",
         "    --   ", "  -- ")
      .changedLinesNumber(1, 1)
      .noInnerChanges()
      .run()

    Test("X_W_W_Z", "X_Z",
         " +++++ ", " + ",
         "       ", "   ")
      .changedLinesNumber(0, 0)
      .noInnerChanges()
      .run()

    Test("X_W 1 W_Y_W 2 W_Z", "X_W 3 W_B_W 4 W_Z",
         "    +       +    ", "    +       +    ",
         "        --       ", "        --       ")
      .changedLinesNumber(1, 1)
      .noInnerChanges()
      .run()

    Test("X_W W W_Z", "X_W W_ W_Z",
         " + + + + ", " + + ++ + ",
         "  ------ ", "  ------- ")
      .changedLinesNumber(1, 2)
      .noInnerChanges()
      .run()

    Test("X_W M W_Z", "X_W W_ W_Z",
         " + + + + ", " + + ++ + ",
         "  ------ ", "  ------- ")
      .changedLinesNumber(1, 2)
      .noInnerChanges()
      .run()
  }


  fun `test trim vs trimExpand for inner ranges`() {
    Test("X M Y Z", "A B Y C",
         "+     +", "+     +",
         " --    ", " --    ")
      .run()

    Test("X M Y Z", "A B Y C",
         "  +    ", "  +    ",
         "--    -", "--    -")
      .run()

    Test("X MZ Y Z", "A BZ Y C",
         "  +     ", "  +     ",
         "----   -", "----   -")
      .run()

    Test("ZX M Y Z", "A B Y C",
         " +     +", "+     +",
         "----    ", " --    ")
      .run()

    Test("X Y", "XY",
         " + ", "  ",
         "---", "--")
      .changedLinesNumber(1, 1)
      .run()
  }

  fun `test Java samples`() {
    Test("System . out.println(\"Hello world\");", "System.out.println(\"Hello world\");",
         "      + +            .            .   ", "                   .            .   ",
         "                     .            .   ", "                   .            .   ")
      .changedLinesNumber(0, 0)
      .run()

    Test(" System . out . println(\"Hello  world\") ; ", "System.out.println(\"Hello world\");",
         "+      + +   + +   .                  .  + +", "                   .            .   ",
         "                        .      -      .     ", "                   .            .   ")
      .run()

    Test("import java.util.Random;_import java.util.List;__class Test {_}", "import java.util.List;_import java.util.Timer;__class Foo {_}",
         "+++++++++++++++++++++++++++++++++++++++++++++++++              ", "++++++++++++++++++++++++++++++++++++++++++++++++             ",
         "                                                       ----    ", "                                                      ---    ")
      .changedLinesNumber(1, 1)
      .run()

    Test("final_int x = 0;", "final int Y = 0;",
         "     +   + + +  ", "     +   + + +  ",
         "          -     ", "          -     ")
      .changedLinesNumber(2, 1)
      .run()

    Test("int X = 0;", "intX = 0;",
         "   + + +  ", "    + +  ",
         "-----     ", "----     ")
      .changedLinesNumber(1, 1)
      .run()
  }

  fun `test Java bad samples`() {
    //TODO

    Test("System.out.println (\"Hello  world\");", "System.out.println(\"Hello world\");",
         "              .   +               .   ", "                   .            .   ",
         "                  - .      -      .   ", "                   .            .   ")
      .run()

    Test("private static final Cleaner NOP = () -> { };_", "private static final Cleaner NOP = () -> { _ };_",
         "       +      +     +       +   + +  +  + +  +", "       +      +     +       +   + +  +  + +++  +",
         "                                           -- ", "                                             -- ")
      .run()

    Test("private static final Cleaner NOP = () -> { };", "private static final Cleaner NOP = () -> { _ };",
         "       +      +     +       +   + +  +  + +  ", "       +      +     +       +   + +  +  + +++  ",
         "                                             ", "                                               ")
      .run()
  }

  fun `test explicit blocks`() {
    Test("X_a_Y_b_Z", "X_a 1 c_Y_b 1 c_Z",
         "         ", "   +++     +++   ",
         "         ", "      -          ")
      .ranged(Range(1, 2, 1, 2))
      .changedLinesNumber(1, 1)
      .run()

    Test("X_a_Y_b_Z", "X_a 1 c_Y_b 1 c_Z",
         "         ", "   ++++    +++   ",
         "         ", "                 ")
      .ranged(Range(1, 2, 1, 2))
      .changedLinesNumber(0, 0)
      .run()

    Test("X_a_Y_b_Z", "X_a 1 c_Y_b 1 c_Z",
         "         ", "              +  ",
         "         ", "           ---   ")
      .ranged(Range(3, 4, 3, 4))
      .changedLinesNumber(1, 1)
      .run()

    Test("X_a_Y_b_Z", "X_a 1 c_Y_b 1 c_Z",
         "         ", "              +  ",
         "  -      ", "          ----   ")
      .ranged(Range(1, 2, 3, 4))
      .changedLinesNumber(1, 1)
      .run()

    Test("X_a_Y_b_Z", "Y_b 1 c_Z",
         "         ", "      +  ",
         "         ", "   ---   ")
      .ranged(Range(3, 4, 1, 2))
      .changedLinesNumber(1, 1)
      .run()

    Test("X_a_Y_b_Z", "Y_c_d_b_Z",
         "         ", "  ++++   ",
         "         ", "         ")
      .ranged(Range(3, 4, 1, 4))
      .changedLinesNumber(0, 0)
      .run()

    Test("X_a_Y_Z", "Y_c_d_Z",
         "       ", "  ++++ ",
         "       ", "       ")
      .ranged(Range(3, 3, 1, 3))
      .changedLinesNumber(0, 0)
      .run()


    Test("X_a_Y_Z", "Y_c_d_Z",
         "       ", "       ",
         "       ", "  ---- ")
      .ranged(Range(3, 3, 1, 3))
      .changedLinesNumber(0, 2)
      .run()

    Test("X_W 1 W_Y_W 2 W_Z", "X_W 3 W_B_W 4 W_Z",
         "    +       +    ", "    +       +    ",
         "        --       ", "        --       ")
      .ranged(Range(2, 4, 2, 4))
      .changedLinesNumber(1, 1)
      .noInnerChanges()
      .run()

    Test("X_W 1 W_Y_W 2 W_Z", "X_W 3 W_B_W 4 W_Z",
         "    +       +    ", "    +       +    ",
         "        --       ", "        --       ")
      .ranged(Range(1, 5, 1, 5))
      .changedLinesNumber(1, 1)
      .noInnerChanges()
      .run()

    Test("X_W 1 W_Y_W 2 W_Z", "X_W 3 W_B_W 4 W_Z",
         "    +       +    ", "    +       +    ",
         "                 ", "                 ")
      .ranged(Range(1, 2, 3, 4))
      .changedLinesNumber(0, 0)
      .noInnerChanges()
      .run()

    Test("X_a Y_Z", "Y_aY_Z",
         "   + + ", "    + ",
         "  ---  ", "  --  ")
      .ranged(Range(1, 2, 1, 2))
      .changedLinesNumber(1, 1)
      .run()

    Test("X_a Y_Z", "X_aY_Z",
         "   + + ", "    + ",
         "  ---  ", "  --  ")
      .ranged(Range(1, 2, 1, 2))
      .changedLinesNumber(1, 1)
      .run()
  }

  private inner class Test(val input1: String, val input2: String,
                           ignored1: String, ignored2: String,
                           result1: String, result2: String) {
    val ignored1: String = ignored1.filterNot { it == '.' }
    val ignored2: String = ignored2.filterNot { it == '.' }
    val result1: String = result1.filterNot { it == '.' }
    val result2: String = result2.filterNot { it == '.' }

    private var inner = true
    private var changedLines: IntPair? = null
    private var range: Range? = null

    fun noInnerChanges(): Test {
      inner = false
      return this
    }

    fun changedLinesNumber(lines1: Int, lines2: Int): Test {
      changedLines = IntPair(lines1, lines2)
      return this
    }

    fun ranged(range: Range): Test {
      this.range = range
      return this
    }

    fun run() {
      assertEquals(input1.length, ignored1.length)
      assertEquals(input1.length, result1.length)
      assertEquals(input2.length, ignored2.length)
      assertEquals(input2.length, result2.length)

      val text1 = parseText(input1)
      val text2 = parseText(input2)

      val ignoredRanges1 = parseIgnored(ignored1)
      val ignoredRanges2 = parseIgnored(ignored2)

      val ignored1 = ComparisonManagerImpl.collectIgnoredRanges(ignoredRanges1)
      val ignored2 = ComparisonManagerImpl.collectIgnoredRanges(ignoredRanges2)

      val lineOffsets1 = LineOffsetsUtil.create(text1)
      val lineOffsets2 = LineOffsetsUtil.create(text2)

      val result = if (range != null) {
        MANAGER.compareLinesWithIgnoredRanges(range!!, text1, text2, lineOffsets1, lineOffsets2, ignored1, ignored2, inner, INDICATOR)
      }
      else {
        MANAGER.compareLinesWithIgnoredRanges(text1, text2, lineOffsets1, lineOffsets2, ignored1, ignored2, inner, INDICATOR)
      }

      val expected = Couple(parseExpected(result1), parseExpected(result2))
      val actual = parseActual(result)
      assertEquals(expected, actual)

      if (changedLines != null) {
        val actualLines = countChangedLines(result)
        assertEquals(changedLines, actualLines)
      }
    }
  }

  private fun parseText(input: String): String {
    return input.replace('_', '\n')
  }

  private fun parseIgnored(ignored: String): List<TextRange> {
    assertTrue(ignored.find { it != ' ' && it != '+' } == null)

    val result = ArrayList<TextRange>()
    ignored.forEachIndexed { index, c ->
      if (c == '+') result += TextRange(index, index + 1)
    }
    return result
  }

  private fun parseExpected(result: String): BitSet {
    assertTrue(result.find { it != ' ' && it != '-' } == null)

    val set = BitSet()
    result.forEachIndexed { index, c ->
      if (c == '-') set.set(index)
    }
    return set
  }

  private fun parseActual(result: List<LineFragment>): Couple<BitSet> {
    val set1 = BitSet()
    val set2 = BitSet()
    result.forEach { fragment ->
      val inner = fragment.innerFragments
      if (inner == null) {
        set1.set(fragment.startOffset1, fragment.endOffset1)
        set2.set(fragment.startOffset2, fragment.endOffset2)
      }
      else {
        inner.forEach { inner ->
          set1.set(fragment.startOffset1 + inner.startOffset1, fragment.startOffset1 + inner.endOffset1)
          set2.set(fragment.startOffset2 + inner.startOffset2, fragment.startOffset2 + inner.endOffset2)
        }
      }
    }
    return Couple(set1, set2)
  }

  private fun countChangedLines(result: List<LineFragment>): IntPair {
    var count1 = 0
    var count2 = 0
    result.forEach {
      count1 += it.endLine1 - it.startLine1
      count2 += it.endLine2 - it.startLine2
    }
    return IntPair(count1, count2)
  }
}