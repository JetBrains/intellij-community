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

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class SuspiciousSystemArraycopyInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SuspiciousSystemArraycopyInspection());

  @Test
  public void testEmptyDst() {
    inspectionFixture.get().doMemberTest("void m(int[] src) {" +
                 "  System.arraycopy(src, 0,/*!Expression expected*/ /*!*/, 0, src.length);" +
                 "}");
  }

  @Test
  public void testEmptySrc() {
    inspectionFixture.get().doMemberTest("void m(int[] dst) {" +
                 "  System.arraycopy(/*!Expression expected*/,/*!*/ 0, dst, 0, 10);" +
                 "}");
  }


  @Test
  public void testLengthAlwaysGreater() {
    inspectionFixture.get().doMemberTest("""
                   public int[] hardCase() {
                           int[] src = new int[] { 1, 2, 3 };
                           int[] dest = new int[] { 4, 5, 6, 7, 8, 9 };
                           System.arraycopy(src, 2, dest, 2, /*Length is always bigger than 'src.length - srcPos' {2}*/2/**/);
                           return dest;
                       }""");
  }

  @Test
  public void testLengthNotAlwaysGreater() {
    inspectionFixture.get().doMemberTest("""
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

  @Test
  public void testRangesNotIntersect() {
    inspectionFixture.get().doMemberTest("""
                   public void process() {
                           int[] src = new int[] { 1, 2, 3, 4 };
                           System.arraycopy(src, 0, src, 2, 2);
                       }""");
  }

  @Test
  public void testRangesIntersect() {
    inspectionFixture.get().doMemberTest("""
                       public void rangesIntersects() {
                           int[] src = new int[] { 1, 2, 3, 4 };
                           System./*Copying to the same array with intersecting ranges*/arraycopy/**/(src, 0, src, 1, 2);
                       }\
                   """);
  }

  @Test
  public void testRangesIntersectSometimes() {
    inspectionFixture.get().doMemberTest("""
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

  @Test
  public void testRangeEndMayBeBiggerStart() {
    inspectionFixture.get().doMemberTest("""
                   public void hardCase(boolean outer) {
                           int[] src = new int[] { 1, 2, 3, 4, 5, 6, 7 };
                           int length = outer ? 1 : 3;
                           int srcPos = outer ? 0 : 3;
                           int destPos = outer ? 1 : 4;
                           System.arraycopy(src, srcPos, src, destPos, length);
                       }""");
  }


  @Test
  public void testCopyFull() {
    inspectionFixture.get().doMemberTest("""
                       public static void copyFull(byte[] a2, byte[] a1) {
                           assert (a1.length == 4);
                           assert (a2.length == 8);
                           System.arraycopy(a1, 0, a2, 4, a1.length);
                       }\
                   """);
  }

  @Test
  public void test248060() {
    inspectionFixture.get().doMemberTest("""
                   public class ArrayCopyExample {
                       private double[] margins = new double[4];

                       public ArrayCopyExample() {}

                       public ArrayCopyExample(ArrayCopyExample original) {
                           System.arraycopy(original.margins, 0, margins, 0, 4);
                       }
                   }""");
  }
}