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

import junit.framework.TestCase

class LineNumberConvertorTest : TestCase() {
  fun testEmpty() {
    doTest(
      {
      },
      {
        checkEmpty(-5, 20)
        checkEmptyInv(-5, 20)
      }
    )
  }

  fun testSingleRange() {
    doTest(
      {
        put(2, 3, 2)
      },
      {
        checkMatch(2, 3, 2)

        checkEmpty(-5, 1)
        checkEmpty(4, 10)

        checkEmptyInv(-5, 2)
        checkEmptyInv(5, 10)
      }
    )
  }

  fun testTwoRanges() {
    doTest(
      {
        put(2, 3, 2)
        put(10, 7, 1)
      },
      {
        checkMatch(2, 3, 2)
        checkMatch(10, 7, 1)

        checkEmpty(-5, 1)
        checkEmpty(4, 9)
        checkEmpty(11, 15)

        checkEmptyInv(-5, 2)
        checkEmptyInv(5, 6)
        checkEmptyInv(8, 12)
      }
    )
  }

  fun testAdjustmentRanges() {
    doTest(
      {
        put(2, 3, 2)
        put(4, 5, 3)
      },
      {
        checkMatch(2, 3, 5)

        checkEmpty(-5, 1)
        checkEmpty(7, 10)

        checkEmptyInv(-5, 2)
        checkEmptyInv(8, 10)
      }
    )
  }

  fun testPartiallyAdjustmentRanges() {
    doTest(
      {
        put(2, 3, 2)
        put(4, 10, 3)
      },
      {
        checkMatch(2, 3, 2)
        checkMatch(4, 10, 3)

        checkEmpty(-5, 1)
        checkEmpty(7, 10)

        checkEmptyInv(-5, 2)
        checkEmptyInv(5, 9)
        checkEmptyInv(13, 15)
      }
    )
  }

  fun testTwoRangesApproximate() {
    doTest(
      {
        put(1, 2, 1)
        put(6, 5, 2)
      },
      {
        checkEmpty(-5, 0)
        checkMatch(1, 2, 1)
        checkEmpty(3, 5)
        checkMatch(6, 5, 2)
        checkEmpty(8, 20)

        checkApproximate(0, 0)
        checkApproximate(1, 2)
        checkApproximate(2, 3)
        checkApproximate(3, 3)
        checkApproximate(4, 3)
        checkApproximate(5, 3)
        checkApproximate(6, 5)
        checkApproximate(7, 6)
        checkApproximate(8, 7)
        checkApproximate(9, 7)
        checkApproximate(10, 7)
        checkApproximate(11, 7)

        checkApproximateInv(0, 0)
        checkApproximateInv(0, 1)
        checkApproximateInv(1, 2)
        checkApproximateInv(2, 3)
        checkApproximateInv(2, 4)
        checkApproximateInv(6, 5)
        checkApproximateInv(7, 6)
        checkApproximateInv(8, 7)
        checkApproximateInv(8, 8)
        checkApproximateInv(8, 9)
        checkApproximateInv(8, 10)
        checkApproximateInv(8, 11)
      }
    )
  }

  fun testNonFairRange() {
    doTest(
      {
        put(1, 2, 1)
        put(6, 5, 2, 4)
      },
      {
        checkEmpty(-5, 0)
        checkMatch(1, 2, 1)
        checkEmpty(3, 20)

        checkApproximate(0, 0)
        checkApproximate(1, 2)
        checkApproximate(2, 3)
        checkApproximate(3, 3)
        checkApproximate(4, 3)
        checkApproximate(5, 3)
        checkApproximate(6, 5)
        checkApproximate(7, 6)
        checkApproximate(8, 7)
        checkApproximate(9, 8)
        checkApproximate(10, 9)
        checkApproximate(11, 9)
        checkApproximate(12, 9)

        checkApproximateInv(0, 0)
        checkApproximateInv(0, 1)
        checkApproximateInv(1, 2)
        checkApproximateInv(2, 3)
        checkApproximateInv(2, 4)
        checkApproximateInv(6, 5)
        checkApproximateInv(7, 6)
        checkApproximateInv(8, 7)
        checkApproximateInv(8, 8)
        checkApproximateInv(8, 9)
        checkApproximateInv(8, 10)
        checkApproximateInv(8, 11)
      })
  }

  //
  // Impl
  //

  private fun doTest(prepare: TestBuilder.() -> Unit, check: Test.() -> Unit) {
    val builder = TestBuilder()
    builder.prepare()
    val test = builder.finish()
    test.check()
  }

  private class TestBuilder {
    private val builder = LineNumberConvertor.Builder()

    fun put(left: Int, right: Int, length: Int) {
      builder.put(left, right, length)
    }

    fun put(left: Int, right: Int, lengthLeft: Int, lengthRight: Int) {
      builder.put(left, right, lengthLeft, lengthRight)
    }

    fun finish(): Test = Test(builder.build())
  }

  private class Test(val convertor: LineNumberConvertor) {
    fun checkMatch(left: Int, right: Int, length: Int = 1) {
      for (i in 0..length - 1) {
        assertEquals(right + i, convertor.convert(left + i))
        assertEquals(left + i, convertor.convertInv(right + i))
      }
    }

    fun checkApproximate(left: Int, right: Int) {
      assertEquals(right, convertor.convertApproximate(left))
    }

    fun checkApproximateInv(left: Int, right: Int) {
      assertEquals(left, convertor.convertApproximateInv(right))
    }

    fun checkEmpty(startLeft: Int, endLeft: Int) {
      for (i in startLeft..endLeft) {
        assertEquals(-1, convertor.convert(i))
      }
    }

    fun checkEmptyInv(startRight: Int, endRight: Int) {
      for (i in startRight..endRight) {
        assertEquals(-1, convertor.convertInv(i))
      }
    }
  }
}
