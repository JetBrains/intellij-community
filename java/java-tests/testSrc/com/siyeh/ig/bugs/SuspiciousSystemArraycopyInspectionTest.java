/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SuspiciousSystemArraycopyInspectionTest extends LightJavaInspectionTestCase {

  public void testEmptyDst() {
    doMemberTest("void m(int[] src) {" +
                 "  System.arraycopy(src, 0,/*!Expression expected*/ /*!*/, 0, src.length);" +
                 "}");
  }

  public void testEmptySrc() {
    doMemberTest("void m(int[] dst) {" +
                 "  System.arraycopy(/*!Expression expected*/,/*!*/ 0, dst, 0, 10);" +
                 "}");
  }


  public void testLengthAlwaysGreater() {
    doMemberTest("""
                   public int[] hardCase() {
                           int[] src = new int[] { 1, 2, 3 };
                           int[] dest = new int[] { 4, 5, 6, 7, 8, 9 };
                           System.arraycopy(src, 2, dest, 2, /*Length is always bigger than 'src.length - srcPos' {2}*/2/**/);
                           return dest;
                       }""");
  }

  public void testLengthNotAlwaysGreater() {
    doMemberTest("""
                   public int[] hardCase(boolean outer) {
                           int[] src = new int[] { 1, 2, 3};
                           int[] dest = new int[] { 4, 5, 6, 7, 8, 9 };
                           int length;
                           if (outer) {
                               length = 3; // maybe this branch is never reached due to outer condition
                           } else {
                               length = 1;
                           }
                           System.arraycopy(src, 2, dest, 2, length);
                           return dest;
                       }""");
  }

  public void testRangesNotIntersect() {
    doMemberTest("""
                   public void process() {
                           int[] src = new int[] { 1, 2, 3, 4 };
                           System.arraycopy(src, 0, src, 2, 2);
                       }""");
  }

  public void testRangesIntersect() {
    doMemberTest("""
                       public void rangesIntersects() {
                           int[] src = new int[] { 1, 2, 3, 4 };
                           System./*Copying to the same array with intersecting ranges*/arraycopy/**/(src, 0, src, 1, 2);
                       }\
                   """);
  }

  public void testRangesIntersectSometimes() {
    doMemberTest("""
                   public void rangesIntersects(boolean outer) {
                           int[] src = new int[] { 1, 2, 3, 4, 5 };
                           int srcPos;
                           if (outer) {
                               srcPos = 0;
                           } else {
                               srcPos = 1; // maybe this branch never reached due to outer condition
                           }
                           System.arraycopy(src, srcPos, src, 2, 2);
                       }""");
  }

  public void testRangeEndMayBeBiggerStart() {
    doMemberTest("""
                   public void hardCase(boolean outer) {
                           int[] src = new int[] { 1, 2, 3, 4, 5, 6, 7 };
                           int length = outer ? 1 : 3;
                           int srcPos = outer ? 0 : 3;
                           int destPos = outer ? 1 : 4;
                           System.arraycopy(src, srcPos, src, destPos, length);
                       }""");
  }


  public void testCopyFull() {
    doMemberTest("""
                       public static void copyFull(byte[] a2, byte[] a1) {
                           assert (a1.length == 4);
                           assert (a2.length == 8);
                           System.arraycopy(a1, 0, a2, 4, a1.length);
                       }\
                   """);
  }

  public void test248060() {
    doMemberTest("""
                   public class ArrayCopyExample {
                       private double[] margins = new double[4];

                       public ArrayCopyExample() {}

                       public ArrayCopyExample(ArrayCopyExample original) {
                           System.arraycopy(original.margins, 0, margins, 0, 4);
                       }
                   }""");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousSystemArraycopyInspection();
  }
}