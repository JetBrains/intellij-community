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
package com.intellij.diff.comparison

import com.intellij.diff.DiffTestCase
import com.intellij.diff.util.IntPair

class ComparisonUtilTest : DiffTestCase() {
  fun testTrimEquals() {
    doTestTrim(true, "", "")
    doTestTrim(true, "", "   ")
    doTestTrim(true, "   ", "   ")
    doTestTrim(true, "\n   ", "  \n")
    doTestTrim(true, "asd ", "asd  ")
    doTestTrim(true, "    asd", "asd")
    doTestTrim(true, "\n\n\n", "\n\n\n")
    doTestTrim(true, "\n  \n  \n ", "  \n \n\n  ")
    doTestTrim(false, "\n\n", "\n\n\n")

    doTestTrim(false, "\nasd ", "asd\n  ")
    doTestTrim(true, "\nasd \n", "\n asd\n  ")
    doTestTrim(false, "x", "y")
    doTestTrim(false, "\n", " ")

    doTestTrim(true, "\t ", "")
    doTestTrim(false, "", "\t\n \n\t")
    doTestTrim(false, "\t", "\n")

    doTestTrim(true, "x", " x")
    doTestTrim(true, "x", "x ")
    doTestTrim(false, "x\n", "x")

    doTestTrim(false, "abc", "a\nb\nc\n")
    doTestTrim(true, "\nx y x\n", "\nx y x\n")
    doTestTrim(false, "\nxyx\n", "\nx y x\n")
    doTestTrim(true, "\nx y x", "\nx y x")
    doTestTrim(false, "\nxyx", "\nx y x")
    doTestTrim(true, "x y x", "x y x")
    doTestTrim(false, "xyx", "x y x")
    doTestTrim(true, "  x y x  ", "x y x")

    doTestTrim(false, "x", "\t\n ")
    doTestTrim(false, "", " x ")
    doTestTrim(false, "", "x ")
    doTestTrim(false, "", " x")
    doTestTrim(false, "xyx", "xxx")
    doTestTrim(false, "xyx", "xYx")
  }

  fun testLineFragment() {
    doTestLineFragment(
        "", "x",
        !0 - 0, !0 - 1,
        !0 - 1, !0 - 1)

    doTestLineFragment(
        "x", "y",
        !0 - 1, !0 - 1,
        !0 - 1, !0 - 1)

    doTestLineFragment(
        "x_", "y_",
        !0 - 2, !0 - 2,
        !0 - 1, !0 - 1)

    doTestLineFragment(
        "x", "y_",
        !0 - 1, !0 - 2,
        !0 - 1, !0 - 2)

    doTestLineFragment(
        "x", "x_",
        !1 - 1, !2 - 2,
        !1 - 1, !1 - 2)

    doTestLineFragment(
        "x_y_z_", "x_Y_z_",
        !2 - 4, !2 - 4,
        !1 - 2, !1 - 2)

    doTestLineFragment(
        "x_y_z_", "x_y_Z_",
        !4 - 6, !4 - 6,
        !2 - 3, !2 - 3)

    doTestLineFragment(
        "x_y_z_", "x_y_Z_",
        !4 - 6, !4 - 6,
        !2 - 3, !2 - 3)

    doTestLineFragment(
        "x_y_z_", "x_y_Z",
        !4 - 6, !4 - 5,
        !2 - 4, !2 - 3)

    doTestLineFragment(
        "x_y_z", "x_y_Z_",
        !4 - 5, !4 - 6,
        !2 - 3, !2 - 4)

    doTestLineFragment(
        " ", "_ ",
        !0 - 0, !0 - 1,
        !0 - 0, !0 - 1)

    doTestLineFragment(
        " ", " _",
        !1 - 1, !2 - 2,
        !1 - 1, !1 - 2)
  }

  //
  // Impl
  //

  private fun doTestLineFragment(string1: String, string2: String,
                                 offsets1: IntPair, offsets2: IntPair,
                                 lines1: IntPair, lines2: IntPair) {
    val fragments = MANAGER.compareLines(parseSource(string1), parseSource(string2), ComparisonPolicy.DEFAULT, INDICATOR)
    assertTrue(fragments.size == 1, "Side: ${fragments.size})")
    val fragment = fragments[0]

    assertEquals(offsets1.val1, fragment.startOffset1, fragment.toString())
    assertEquals(offsets1.val2, fragment.endOffset1, fragment.toString())
    assertEquals(offsets2.val1, fragment.startOffset2, fragment.toString())
    assertEquals(offsets2.val2, fragment.endOffset2, fragment.toString())
    assertEquals(lines1.val1, fragment.startLine1, fragment.toString())
    assertEquals(lines1.val2, fragment.endLine1, fragment.toString())
    assertEquals(lines2.val1, fragment.startLine2, fragment.toString())
    assertEquals(lines2.val2, fragment.endLine2, fragment.toString())
  }

  private fun doTestTrim(expected: Boolean, string1: String, string2: String) {
    doTest(expected, string1, string2, ComparisonPolicy.TRIM_WHITESPACES)
  }

  private fun doTest(expected: Boolean, string1: String, string2: String, policy: ComparisonPolicy) {
    val result = MANAGER.isEquals(string1, string2, policy)
    assertEquals(expected, result, "---\n$string1\n---\n$string2\n---")
  }

  //
  // Helpers
  //

  operator fun Int.not(): LineColHelper = LineColHelper(this)
  operator fun LineColHelper.minus(col: Int): IntPair = IntPair(this.line, col)

  inner class LineColHelper(val line: Int) {
  }
}
