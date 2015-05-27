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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.Side;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;

public class LineNumberConvertorCorrectorTest extends UsefulTestCase {
  public void testUnmodified() {
    Test test = new Test();
    test.equal(0, 0, 10, Side.LEFT);
    test.equal(0, 0, 12, Side.RIGHT);
    test.finish();

    test.checkStrictSymmetrical();
    test.ensureMatchedCount(10, 12);
  }

  public void testEqual1() {
    Test test = new Test();
    test.equal(0, 0, 10, Side.LEFT);
    test.equal(0, 0, 10, Side.RIGHT);
    test.finish();

    test.change(4, 3, 5, Side.LEFT);

    test.checkStrictSymmetrical();
    test.ensureMatchedCount(12, 7);
  }

  public void testEqual2() {
    Test test = new Test();
    test.equal(0, 0, 10, Side.LEFT);
    test.equal(0, 0, 10, Side.RIGHT);
    test.finish();

    test.change(4, 5, 3, Side.RIGHT);

    test.checkStrictSymmetrical();
    test.ensureMatchedCount(5, 8);
  }

  public void testEqual3() {
    Test test = new Test();
    test.equal(0, 0, 10, Side.LEFT);
    test.equal(0, 0, 10, Side.RIGHT);
    test.finish();

    test.change(4, 3, 3, Side.LEFT);

    test.checkStrictSymmetrical();
    test.ensureMatchedCount(10, 7);
  }

  public void testEqual4() {
    Test test = new Test();
    test.equal(0, 0, 15, Side.LEFT);
    test.equal(0, 0, 15, Side.RIGHT);
    test.finish();

    test.change(4, 3, 5, Side.LEFT);
    test.checkStrictSymmetrical();
    test.change(1, 2, 1, Side.RIGHT);
    test.checkStrictSymmetrical();
    test.change(12, 3, 1, Side.LEFT);
    test.checkStrictSymmetrical();

    test.ensureMatchedCount(13, 8);
  }

  public void testInsideModifiedRange() {
    Test test = new Test();
    test.equal(0, 0, 15, Side.LEFT);
    test.equal(0, 0, 15, Side.RIGHT);
    test.finish();

    test.change(0, 10, 15, Side.LEFT);
    test.checkStrictSymmetrical();
    test.change(0, 8, 6, Side.LEFT);
    test.checkStrictSymmetrical();
    test.change(2, 4, 2, Side.LEFT);
    test.checkStrictSymmetrical();
  }

  private static class Test {
    private final LineNumberConvertor.Builder myBuilder = new LineNumberConvertor.Builder();
    private LineNumberConvertor myConvertor;
    private int myLength = 0; // search for strict matchings in this boundaries (*2 - just in case)

    public void equal(int onesideStart, int twosideStart, int length, @NotNull Side side) {
      if (side.isLeft()) {
        myBuilder.put1(onesideStart, twosideStart, length);
      }
      else {
        myBuilder.put2(onesideStart, twosideStart, length);
      }
      myLength = Math.max(myLength, onesideStart + length);
      myLength = Math.max(myLength, twosideStart + length);
    }

    public void finish() {
      myConvertor = myBuilder.build();
    }

    public void change(int onesideLine, int oldLength, int newLength, @NotNull Side side) {
      myConvertor.handleOnesideChange(onesideLine, onesideLine + oldLength, newLength - oldLength, side);
      myLength = Math.max(myLength, myLength + newLength - oldLength);
    }

    public void checkStrictSymmetrical() {
      for (int i = 0; i < myLength * 2; i++) {
        int value1 = myConvertor.convertInv1(i);
        if (value1 != -1) assertEquals(i, myConvertor.convert1(value1));

        int value2 = myConvertor.convertInv2(i);
        if (value2 != -1) assertEquals(i, myConvertor.convert2(value2));

        int value3 = myConvertor.convert1(i);
        if (value3 != -1) assertEquals(i, myConvertor.convertInv1(value3));

        int value4 = myConvertor.convert2(i);
        if (value4 != -1) assertEquals(i, myConvertor.convertInv2(value4));
      }
    }

    public void ensureMatchedCount(int minimumMatched1, int minimumMatched2) {
      int counter1 = 0;
      int counter2 = 0;
      for (int i = 0; i < myLength * 2; i++) {
        if (myConvertor.convert1(i) != -1) counter1++;
        if (myConvertor.convert2(i) != -1) counter2++;
      }
      assertEquals(minimumMatched1, counter1);
      assertEquals(minimumMatched2, counter2);
    }

    public void printMatchings() {
      for (int i = 0; i < myLength * 2; i++) {
        int value = myConvertor.convert1(i);
        if (value != -1) System.out.println("L: " + i + " - " + value);
      }

      for (int i = 0; i < myLength * 2; i++) {
        int value = myConvertor.convert2(i);
        if (value != -1) System.out.println("R: " + i + " - " + value);
      }
    }
  }
}
