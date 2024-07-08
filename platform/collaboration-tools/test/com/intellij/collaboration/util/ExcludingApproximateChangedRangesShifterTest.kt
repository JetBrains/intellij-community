// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.util.Range
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

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

  /*
  [7, 24) - [7, 25), [33, 41) - [34, 47) ->
[20, 25) - [20, 39), [38, 38) - [52, 60)
   */
  @Test
  fun `test mixed 1`() {
    test({
           r(7, 25, 7, 24)
           r(34, 47, 33, 41)
         }, {
           r(20, 39, 20, 25)
           r(52, 60, 38, 38)
         }, {
           r(7, 20, 7, 24)
           r(48, 52, 33, 41)
           r(60, 69, 33, 41)
         })
  }

  @Test
  fun `test random`() {
    val random = Random(System.currentTimeMillis())
    for (i in 0..100000) {
      val early = buildFairRanges(2, random)
      val later = buildFairRanges(4, random)
      try {
        ExcludingApproximateChangedRangesShifter.shift(early, later)
      }
      catch (e: Throwable) {
        throw Exception("$early -> $later", e)
      }
    }
  }

  private fun buildFairRanges(count: Int, random: Random): List<Range> {
    val result = mutableListOf<Range>()
    var last1 = 0
    var last2 = 0
    for (i in 0 until count) {
      val unchanged = random.nextInt(20) + 1
      val start1 = last1 + unchanged
      last1 = start1 + random.nextInt(20)
      val start2 = last2 + unchanged
      last2 = start2 + random.nextInt(20)
      val range = Range(start1, last1, start2, last2)
      if (range.isEmpty) continue
      result += range
    }
    DiffIterableUtil.setVerifyEnabled(true)
    val iterable = DiffIterableUtil.fair(DiffIterableUtil.create(result, last1 + 10, last2 + 10))
    DiffIterableUtil.verifyFair(iterable)
    DiffIterableUtil.setVerifyEnabled(false)
    return result
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
