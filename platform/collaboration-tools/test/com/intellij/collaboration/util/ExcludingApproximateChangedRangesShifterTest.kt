// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.diff.util.Range
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
      r(0, 5, 0, 0)
      r(5, 10, 1, 5)
    }
    test(earlyBuilder, {}, earlyBuilder)
  }

  @Test
  fun `test no early`() {
    test({ }, {
      r(0, 5, 0, 0)
      r(5, 10, 1, 5)
    }, { })
  }

  @Test
  fun `test beginning intersection`() {
    test({
           r(5, 10, 5, 10)
         }, {
           r(4, 7, 4, 7)
         }, {
           r(7, 10, 5, 10)
         })
  }

  @Test
  fun `test end intersection`() {
    test({
           r(5, 10, 5, 10)
         }, {
           r(7, 12, 7, 12)
         }, {
           r(5, 7, 5, 10)
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
           r(6, 6, 6, 8)
         }, {
           r(5, 6, 5, 10)
           r(6, 8, 5, 10)
         })
  }

  @Test
  fun `test later inside and no intersect`() {
    test({
           r(5, 10, 5, 10)
           r(25, 30, 25, 30)
         }, {
           r(6, 6, 6, 8)
         }, {
           r(5, 6, 5, 10)
           r(6, 8, 5, 10)
           r(23, 28, 25, 30)
         })
  }

  @Test
  fun `test early inside and no intersect`() {
    test({
           r(5, 10, 5, 10)
           r(25, 30, 25, 30)
         }, {
           r(1, 1, 1, 12)
         }, {
           r(14, 19, 25, 30)
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
    val result = ExcludingApproximateChangedRangesShifter.shift(early, later)
    val expectedResult = RangesCollector().apply {
      resultBuilder()
    }.ranges
    assertEquals(expectedResult, result)
  }
}
