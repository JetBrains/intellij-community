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

import com.intellij.testFramework.UsefulTestCase

class LineNumberConvertorTest : UsefulTestCase() {
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
          assertEquals(0, convertor.convertApproximate1(0))
          assertEquals(2, convertor.convertApproximate1(1))
          assertEquals(3, convertor.convertApproximate1(2))
          assertEquals(3, convertor.convertApproximate1(3))
          assertEquals(3, convertor.convertApproximate1(4))
          assertEquals(3, convertor.convertApproximate1(5))
          assertEquals(5, convertor.convertApproximate1(6))
          assertEquals(6, convertor.convertApproximate1(7))
          assertEquals(7, convertor.convertApproximate1(8))
          assertEquals(7, convertor.convertApproximate1(9))
        }
    )
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
      builder.put1(left, right, length)
    }

    fun finish(): Test = Test(builder.build())
  }

  private class Test(val convertor: LineNumberConvertor) {
    fun checkMatch(left: Int, right: Int, length: Int) {
      for (i in 0..length - 1) {
        assertEquals(right + i, convertor.convert1(left + i))
        assertEquals(left + i, convertor.convertInv1(right + i))
      }
    }

    fun checkEmpty(start: Int, end: Int) {
      for (i in start..end) {
        assertEquals(-1, convertor.convert1(i))
      }
    }

    fun checkEmptyInv(start: Int, end: Int) {
      for (i in start..end) {
        assertEquals(-1, convertor.convertInv1(i))
      }
    }
  }
}
