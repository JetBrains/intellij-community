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

import com.intellij.testFramework.UsefulTestCase;

public class LineNumberConvertorTest extends UsefulTestCase {
  public void testEmpty() {
    Test test = new Test();
    test.finish();

    test.checkEmpty(-5, 20);

    test.checkEmptyInv(-5, 20);
  }

  public void testSingleRange() {
    Test test = new Test();
    test.put(2, 3, 2);
    test.finish();

    test.checkMatch(2, 3, 2);

    test.checkEmpty(-5, 1);
    test.checkEmpty(4, 10);

    test.checkEmptyInv(-5, 2);
    test.checkEmptyInv(5, 10);
  }

  public void testTwoRanges() {
    Test test = new Test();
    test.put(2, 3, 2);
    test.put(10, 7, 1);
    test.finish();

    test.checkMatch(2, 3, 2);
    test.checkMatch(10, 7, 1);

    test.checkEmpty(-5, 1);
    test.checkEmpty(4, 9);
    test.checkEmpty(11, 15);

    test.checkEmptyInv(-5, 2);
    test.checkEmptyInv(5, 6);
    test.checkEmptyInv(8, 12);
  }

  public void testAdjustmentRanges() {
    Test test = new Test();
    test.put(2, 3, 2);
    test.put(4, 5, 3);
    test.finish();

    test.checkMatch(2, 3, 5);

    test.checkEmpty(-5, 1);
    test.checkEmpty(7, 10);

    test.checkEmptyInv(-5, 2);
    test.checkEmptyInv(8, 10);
  }

  public void testPartiallyAdjustmentRanges() {
    Test test = new Test();
    test.put(2, 3, 2);
    test.put(4, 10, 3);
    test.finish();

    test.checkMatch(2, 3, 2);
    test.checkMatch(4, 10, 3);

    test.checkEmpty(-5, 1);
    test.checkEmpty(7, 10);

    test.checkEmptyInv(-5, 2);
    test.checkEmptyInv(5, 9);
    test.checkEmptyInv(13, 15);
  }

  public void testTwoRangesApproximate() {
    Test test = new Test();
    test.put(1, 2, 1);
    test.put(6, 5, 2);
    test.finish();

    assertEquals(0, test.myConvertor.convertApproximate1(0));
    assertEquals(2, test.myConvertor.convertApproximate1(1));
    assertEquals(3, test.myConvertor.convertApproximate1(2));
    assertEquals(3, test.myConvertor.convertApproximate1(3));
    assertEquals(3, test.myConvertor.convertApproximate1(4));
    assertEquals(3, test.myConvertor.convertApproximate1(5));
    assertEquals(5, test.myConvertor.convertApproximate1(6));
    assertEquals(6, test.myConvertor.convertApproximate1(7));
    assertEquals(7, test.myConvertor.convertApproximate1(8));
    assertEquals(7, test.myConvertor.convertApproximate1(9));
  }

  private static class Test {
    private final LineNumberConvertor.Builder myBuilder = new LineNumberConvertor.Builder();
    private LineNumberConvertor myConvertor;

    public void put(int left, int right, int length) {
      myBuilder.put1(left, right, length);
    }

    public void finish() {
      myConvertor = myBuilder.build();
    }

    public void checkMatch(int left, int right, int length) {
      for (int i = 0; i < length; i++) {
        assertEquals(right + i, myConvertor.convert1(left + i));
        assertEquals(left + i, myConvertor.convertInv1(right + i));
      }
    }

    public void checkEmpty(int start, int end) {
      for (int i = start; i <= end; i++) {
        assertEquals(-1, myConvertor.convert1(i));
      }
    }

    public void checkEmptyInv(int start, int end) {
      for (int i = start; i <= end; i++) {
        assertEquals(-1, myConvertor.convertInv1(i));
      }
    }
  }
}
