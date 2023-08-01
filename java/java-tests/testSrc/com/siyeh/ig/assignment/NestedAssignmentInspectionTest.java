// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class NestedAssignmentInspectionTest extends LightJavaInspectionTestCase {

  @SuppressWarnings("MismatchedReadAndWriteOfArray")
  public void testLambda() {
    doTest("class Test {" +
           " {" +
           "    int[] array = new int[1];" +
           "    Runnable r = () -> array[0] = 0;" +
           " }" +
           "}");
  }

  public void testSwitchExpression() {
    doTest("""
             class Main {
                 int test(int i) {
                     int j = switch(i) {
                         default -> /*Result of assignment expression used*/i = 2/**/;
                     };
                     return j+i;
                 }
             }""");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new NestedAssignmentInspection();
  }

}