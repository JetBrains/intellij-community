// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.openapi.vcs.ex.Range
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExcludingApproximateChangedRangesShifterTest {
  @Test
  fun `test empty`() {
    test({}, {}, {})
  }

  @Test
  fun `test unchanged`() {
    val earlyBuilder: RangesCollector.() -> Unit = {
      r(0, 0, 0, 5)
      r(1, 5, 5, 10)
    }
    test(earlyBuilder, {}, earlyBuilder)
  }

  @Test
  fun `test no early`() {
    test({ }, {
      r(0, 0, 0, 5)
      r(1, 5, 5, 10)
    }, { })
  }

  @Test
  fun `test beginning intersection`() {
    test({
           r(5, 10, 5, 10)
         }, {
           r(4, 7, 4, 7)
         }, {
           r(5, 10, 7, 10)
         })
  }

  @Test
  fun `test end intersection`() {
    test({
           r(5, 10, 5, 10)
         }, {
           r(7, 12, 7, 12)
         }, {
           r(5, 10, 5, 7)
         })
  }

  @Test
  fun `test early inside`() {
    test({
           r(5, 10, 5, 10)
         }, {
           r(4, 12, 4, 12)
         }, {
         })
  }

  @Test
  fun `test later inside`() {
    test({
           r(5, 10, 5, 10)
         }, {
           r(6, 8, 6, 6)
         }, {
           r(5, 10, 5, 6)
           r(5, 10, 6, 8)
         })
  }

  @Test
  fun `test later inside and no intersect`() {
    test({
           r(5, 10, 5, 10)
           r(25, 30, 25, 30)
         }, {
           r(6, 8, 6, 6)
         }, {
           r(5, 10, 5, 6)
           r(5, 10, 6, 8)
           r(25, 30, 23, 28)
         })
  }

  @Test
  fun `test early inside and no intersect`() {
    test({
           r(5, 10, 5, 10)
           r(25, 30, 25, 30)
         }, {
           r(1, 12, 1, 1)
         }, {
           r(25, 30, 14, 19)
         })
  }

  private class RangesCollector {
    val ranges = mutableListOf<Range>()

    fun r(vcsStart: Int, vcsEnd: Int, start: Int, end: Int) {
      ranges.add(Range(start, end, vcsStart, vcsEnd))
    }
  }

  private fun test(earlyBuilder: RangesCollector.() -> Unit,
                   laterBuilder: RangesCollector.() -> Unit,
                   resultBuilder: RangesCollector.() -> Unit) {
    val early = RangesCollector().apply {
      earlyBuilder()
    }.ranges
    val later = RangesCollector().apply {
      laterBuilder()
    }.ranges
    val result = ExcludingApproximateChangedRangesShifter.shift(early, later).map { RangeWrapper(it) }
    val expectedResult = RangesCollector().apply {
      resultBuilder()
    }.ranges.map { RangeWrapper(it) }
    assertEquals(expectedResult, result)
  }

  // for equals/hashCode
  private class RangeWrapper(private val range: Range) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as RangeWrapper

      if (range.line1 != other.range.line1) return false
      if (range.line2 != other.range.line2) return false
      if (range.vcsLine1 != other.range.vcsLine1) return false
      if (range.vcsLine2 != other.range.vcsLine2) return false

      return true
    }

    override fun hashCode(): Int {
      var result = range.line1
      result = 31 * result + range.line2
      result = 31 * result + range.vcsLine1
      result = 31 * result + range.vcsLine2
      return result
    }

    override fun toString(): String = range.toString()
  }
}
