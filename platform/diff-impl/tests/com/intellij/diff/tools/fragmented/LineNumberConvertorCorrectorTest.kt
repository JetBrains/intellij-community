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

import com.intellij.diff.util.Side
import com.intellij.testFramework.UsefulTestCase

class LineNumberConvertorCorrectorTest : UsefulTestCase() {
  fun testUnmodified() {
    doTest(
        {
          equal(0, 0, 10, Side.LEFT)
          equal(0, 0, 12, Side.RIGHT)
        },
        {
          checkStrictSymmetrical()
          ensureMatchedCount(10, 12)
        }
    )
  }

  fun testEqual1() {
    doTest(
        {
          equal(0, 0, 10, Side.LEFT)
          equal(0, 0, 10, Side.RIGHT)
        },
        {
          change(4, 3, 5, Side.LEFT)

          checkStrictSymmetrical()
          ensureMatchedCount(12, 7)
        }
    )
  }

  fun testEqual2() {
    doTest(
        {
          equal(0, 0, 10, Side.LEFT)
          equal(0, 0, 10, Side.RIGHT)
        },
        {
          change(4, 5, 3, Side.RIGHT)

          checkStrictSymmetrical()
          ensureMatchedCount(5, 8)
        }
    )
  }

  fun testEqual3() {
    doTest(
        {
          equal(0, 0, 10, Side.LEFT)
          equal(0, 0, 10, Side.RIGHT)
        },
        {
          change(4, 3, 3, Side.LEFT)

          checkStrictSymmetrical()
          ensureMatchedCount(10, 7)
        }
    )
  }

  fun testEqual4() {
    doTest(
        {
          equal(0, 0, 15, Side.LEFT)
          equal(0, 0, 15, Side.RIGHT)
        },
        {
          change(4, 3, 5, Side.LEFT)
          checkStrictSymmetrical()
          change(1, 2, 1, Side.RIGHT)
          checkStrictSymmetrical()
          change(12, 3, 1, Side.LEFT)
          checkStrictSymmetrical()

          ensureMatchedCount(13, 8)
        }
    )
  }

  fun testInsideModifiedRange() {
    doTest(
        {
          equal(0, 0, 15, Side.LEFT)
          equal(0, 0, 15, Side.RIGHT)
        },
        {
          change(0, 10, 15, Side.LEFT)
          checkStrictSymmetrical()
          change(0, 8, 6, Side.LEFT)
          checkStrictSymmetrical()
          change(2, 4, 2, Side.LEFT)
          checkStrictSymmetrical()
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
    private var maxLength = 0 // search for strict matchings in this boundaries (*2 - just in case)

    fun equal(onesideStart: Int, twosideStart: Int, length: Int, side: Side) {
      if (side.isLeft) {
        builder.put1(onesideStart, twosideStart, length)
      }
      else {
        builder.put2(onesideStart, twosideStart, length)
      }
      maxLength = Math.max(maxLength, onesideStart + length)
      maxLength = Math.max(maxLength, twosideStart + length)
    }

    fun finish(): Test = Test(builder.build(), maxLength)
  }

  private class Test(val convertor: LineNumberConvertor, var length: Int) {
    fun change(onesideLine: Int, oldLength: Int, newLength: Int, side: Side) {
      convertor.handleOnesideChange(onesideLine, onesideLine + oldLength, newLength - oldLength, side)
      length = Math.max(length, length + newLength - oldLength)
    }

    fun checkStrictSymmetrical() {
      for (i in 0..length * 2) {
        val value1 = convertor.convertInv1(i)
        if (value1 != -1) assertEquals(i, convertor.convert1(value1))

        val value2 = convertor.convertInv2(i)
        if (value2 != -1) assertEquals(i, convertor.convert2(value2))

        val value3 = convertor.convert1(i)
        if (value3 != -1) assertEquals(i, convertor.convertInv1(value3))

        val value4 = convertor.convert2(i)
        if (value4 != -1) assertEquals(i, convertor.convertInv2(value4))
      }
    }

    fun ensureMatchedCount(minimumMatched1: Int, minimumMatched2: Int) {
      var counter1 = 0
      var counter2 = 0
      for (i in 0..length * 2) {
        if (convertor.convert1(i) != -1) counter1++
        if (convertor.convert2(i) != -1) counter2++
      }
      assertEquals(minimumMatched1, counter1)
      assertEquals(minimumMatched2, counter2)
    }

    fun printMatchings() {
      for (i in 0..length * 2 - 1) {
        val value = convertor.convert1(i)
        if (value != -1) println("L: $i - $value")
      }

      for (i in 0..length * 2 - 1) {
        val value = convertor.convert2(i)
        if (value != -1) println("R: $i - $value")
      }
    }
  }
}
