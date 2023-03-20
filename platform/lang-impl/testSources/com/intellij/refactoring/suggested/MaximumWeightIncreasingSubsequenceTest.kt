// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import junit.framework.TestCase

class MaximumWeightIncreasingSubsequenceTest : TestCase() {
  fun testLongestIncreasingSubsequence() {
    fun weight(item: Int) = 1.0

    doTest(
      listOf(3, 5, 1, 9, 4, 2, 1),
      ::weight,
      listOf(3, 5, 9)
    )
    doTest(
      listOf(1),
      ::weight,
      listOf(1)
    )
    doTest(
      listOf(0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15),
      ::weight,
      listOf(0, 4, 6, 9, 13, 15)
    )
  }

  fun testHighestSumIncreasingSubsequence() {
    fun weight(item: Int) = item.toDouble()

    doTest(
      listOf(1, 101, 2, 3, 100, 4, 5),
      ::weight,
      listOf(1, 2, 3, 100)
    )
    doTest(
      listOf(10, 1, 5, 4, 3),
      ::weight,
      listOf(10)
    )
    doTest(
      listOf(3, 2, 6, 4, 5, 1),
      ::weight,
      listOf(3, 4, 5)
    )
  }

  private fun doTest(list: List<Int>, valueFunction: (Int) -> Double, expectedSubsequence: List<Int>) {
    val indices = findMaximumWeightIncreasingSubsequence(list, valueFunction)
    var prev = -1
    for (index in indices) {
      if (prev >= 0) {
        assertTrue(index > prev)
        assertTrue(list[index] > list[prev])
      }
      prev = index
    }
    assertEquals(expectedSubsequence, indices.map { list[it] })
  }
}