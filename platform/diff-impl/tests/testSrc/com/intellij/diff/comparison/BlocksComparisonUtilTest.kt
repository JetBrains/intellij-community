// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison

import com.intellij.diff.HeavyDiffTestCase
import com.intellij.diff.fragments.DiffFragmentImpl
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import java.util.*

class BlocksComparisonUtilTest : HeavyDiffTestCase() {
  fun `test simple blocks`() {
    Test("X_a_Y_c_Z", "X_  a__Y__ c_Z")
      .blocks(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .expected(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .default()

    Test("X_a_Y_c_Z", "X_  a__Y__ c_Z")
      .blocks(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .expected(
        Range(2, 2, 2, 3), Range(3, 3, 4, 5))
      .trim()
      .ignore()

    Test("X_a_Y_c_Z", "X_  a__Y__ c_Z")
      .blocks(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .expected(
      )
      .ignore_chunks()
  }

  fun `test changes outside of blocks are ignored`() {
    Test("X_a_Y_c_Z", "N_  a__N__ c_N")
      .blocks(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .expected(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .default()

    Test("X_X_X_a_Y_c_Z", "N_  a__N__ c_N")
      .blocks(
        Range(5, 6, 4, 6))
      .expected(
        Range(5, 6, 4, 6))
      .default()
  }

  fun `test multiple fragments in a block`() {
    Test("X_a_b_c_Y", "X_a1_ b_c2_Y")
      .blocks(
        Range(1, 4, 1, 4))
      .expected(
        Range(1, 4, 1, 4))
      .default()

    Test("X_a_b_c_Y", "X_a1_ b_c2_Y")
      .blocks(
        Range(1, 4, 1, 4))
      .expected(
        Range(1, 2, 1, 2), Range(3, 4, 3, 4))
      .trim()
      .ignore()
      .ignore_chunks()
  }

  fun `test fragments respect ignore options`() {
    Test("X_a_b_c_Y", "X_a1_ b_c2_Y")
      .blocks(
        Range(1, 4, 1, 4))
      .expected(
        Range(1, 4, 1, 4))
      .default()

    Test("X_Y", "X_a_c_Y")
      .blocks(
        Range(1, 1, 1, 3))
      .expected(
        Range(1, 1, 1, 3))
      .default()
      .trim()
      .ignore()
      .ignore_chunks()

    Test("X_Y", "X___Y")
      .blocks(
        Range(1, 1, 1, 3))
      .expected(
        Range(1, 1, 1, 3))
      .default()
      .trim()
      .ignore()

    Test("X_Y", "X___Y")
      .blocks(
        Range(1, 1, 1, 3))
      .expected(
      )
      .ignore_chunks()
  }

  fun `test leading-trailing empty lines`() {
    Test("X_a__Y", "X__y_Y")
      .blocks(
        Range(1, 3, 1, 3))
      .expected(
        Range(1, 2, 1, 1), Range(3, 3, 2, 3))
      .default()
      .trim()
      .ignore()

    Test("X_a__", "X__y_")
      .blocks(
        Range(1, 3, 1, 3))
      .expected(
        Range(1, 2, 1, 1), Range(3, 3, 2, 3))
      .default()
      .trim()
      .ignore()

    Test("a__", "_y_")
      .blocks(
        Range(0, 2, 0, 2))
      .expected(
        Range(0, 1, 0, 0), Range(2, 2, 1, 2))
      .default()
      .trim()
      .ignore()

    Test("a__", "_y_")
      .blocks(
        Range(0, 3, 0, 3))
      .expected(
        Range(0, 1, 0, 0), Range(2, 2, 1, 2))
      .default()
      .trim()
      .ignore()

    Test("a_", "_y")
      .blocks(
        Range(0, 2, 0, 2))
      .expected(
        Range(0, 1, 0, 0), Range(2, 2, 1, 2))
      .default()
      .trim()
      .ignore()
  }

  fun `test inner changes`() {
    Test("X_a_Y", "X_b_Y",
         "  -  ", "  -  ")
      .blocks(
        Range(1, 2, 1, 2)
      )
      .default()
      .trim()
      .ignore()
      .ignore_chunks()

    Test("X_a_Y", "X_  a_b_Y",
         "     ", "  --  -- ")
      .blocks(
        Range(1, 2, 1, 3)
      )
      .default()

    Test("X_a_Y", "X_  a_b_Y",
         "     ", "      -- ")
      .blocks(
        Range(1, 2, 1, 3)
      )
      .expected(
        Range(2, 2, 2, 3)
      )
      .trim()
      .ignore()

    Test("X_a b_Y", "X_a  b_Y",
         "       ", "   -    ")
      .blocks(
        Range(1, 2, 1, 2)
      )
      .default()
      .trim()

    Test("X_a b_Y", "X_a  b_Y",
         "       ", "        ")
      .blocks(
        Range(1, 2, 1, 2)
      )
      .ignore()
      .ignore_chunks()

    Test("X_a_Y_c_Z", "X_ a__Y__ c_Z",
         "         ", "  -  -  --   ")
      .blocks(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .expected(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .default()

    Test("X_a_Y_c_Z", "X_ a__Y__ c_Z",
         "         ", "     -  -    ")
      .blocks(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .expected(
        Range(2, 2, 2, 3), Range(3, 3, 4, 5))
      .trim()

    Test("X_a_Y_c_Z", "X_ a__Y__ c_Z",
         "         ", "             ")
      .blocks(
        Range(1, 2, 1, 3), Range(3, 4, 4, 6))
      .expected(
        Range(2, 2, 2, 3), Range(3, 3, 4, 5))
      .ignore()
  }

  fun `test inner changes for inserted empty line`() {
    Test("X_Y", "X_ _Y",
         "   ", "  -- ")
      .blocks(
        Range(1, 1, 1, 2)
      )
      .trim()
      .default()

    Test("X_Y", "X_ _Y",
         "   ", "     ")
      .blocks(
        Range(1, 1, 1, 2)
      )
      .expected(
        Range(1, 1, 1, 2)
      )
      .ignore()

    Test("X_Y", "X_ _Y",
         "   ", "     ")
      .blocks(
        Range(1, 1, 1, 2)
      )
      .expected(
      )
      .ignore_chunks()
  }

  fun `test inner changes with leading-trailing empty lines`() {
    Test("X_a__Y", "X__y_Y",
         "  --  ", "   -- ")
      .blocks(
        Range(1, 3, 1, 3))
      .expected(
        Range(1, 2, 1, 1), Range(3, 3, 2, 3))
      .default()
      .trim()
      .ignore()

    Test("a__", "_y_",
         "-- ", " --")
      .blocks(
        Range(0, 2, 0, 2))
      .expected(
        Range(0, 1, 0, 0), Range(2, 2, 1, 2))
      .default()
      .trim()
      .ignore()
  }

  private inner class Test(val input1: String, val input2: String,
                           val inner1: String? = null, val inner2: String? = null) {
    var blocks: List<Range>? = null
    var expected: List<Range>? = null

    fun expected(vararg ranges: Range): Test {
      expected = ranges.toList()
      return this
    }


    fun blocks(vararg ranges: Range): Test {
      blocks = ranges.toList()
      return this
    }

    fun default(): Test {
      doTest(IgnorePolicy.DEFAULT)
      return this
    }

    fun trim(): Test {
      doTest(IgnorePolicy.TRIM_WHITESPACES)
      return this
    }

    fun ignore(): Test {
      doTest(IgnorePolicy.IGNORE_WHITESPACES)
      return this
    }

    fun ignore_chunks(): Test {
      doTest(IgnorePolicy.IGNORE_WHITESPACES_CHUNKS)
      return this
    }


    private fun doTest(ignorePolicy: IgnorePolicy) {
      if (expected == null && (inner1 == null || inner2 == null)) throw IllegalArgumentException()

      if (expected != null) {
        val fragments1 = compareExplicitBlocks(parseSource(input1), parseSource(input2), blocks!!, HighlightPolicy.BY_LINE, ignorePolicy)
        val fragments2 = compareExplicitBlocks(parseSource(input1), parseSource(input2), blocks!!, HighlightPolicy.BY_WORD, ignorePolicy)

        assertEquals(expected!!, fragments1.toRanges())
        assertEquals(expected!!, fragments2.toRanges())
      }

      if (inner1 != null && inner2 != null) {
        val fragments = compareExplicitBlocks(parseSource(input1), parseSource(input2), blocks!!, HighlightPolicy.BY_WORD, ignorePolicy)

        val expected1 = parseInnerExpected(inner1)
        val expected2 = parseInnerExpected(inner2)

        val actual1 = parseInnerActual(fragments, Side.LEFT)
        val actual2 = parseInnerActual(fragments, Side.RIGHT)

        assertEquals(expected1, actual1)
        assertEquals(expected2, actual2)
      }
    }

    private fun parseInnerExpected(inner: String): BitSet {
      val set = BitSet()
      inner.forEachIndexed { index, c ->
        if (c == '-') set.set(index)
      }
      return set
    }

    private fun parseInnerActual(fragments: List<LineFragment>, side: Side): BitSet {
      val set = BitSet()
      fragments.flatMap { fragment ->
        fragment.innerFragments?.map {
          DiffFragmentImpl(fragment.startOffset1 + it.startOffset1, fragment.startOffset1 + it.endOffset1,
                           fragment.startOffset2 + it.startOffset2, fragment.startOffset2 + it.endOffset2)
        } ?: listOf(fragment)
      }.forEach {
        val start = side.getStartOffset(it)
        val end = side.getEndOffset(it)
        set.set(start, end)
      }
      return set
    }

    private fun List<LineFragment>.toRanges(): List<Range> {
      return this.map { Range(it.startLine1, it.endLine1, it.startLine2, it.endLine2) }
    }
  }
}