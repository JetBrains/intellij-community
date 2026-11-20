// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.assignment;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class NestedAssignmentInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new NestedAssignmentInspection());

  @Test
  @SuppressWarnings("MismatchedReadAndWriteOfArray")
  public void testLambda() {
    inspectionFixture.get().doTest("class Test {" +
           " {" +
           "    int[] array = new int[1];" +
           "    Runnable r = () -> array[0] = 0;" +
           " }" +
           "}");
  }

  @Test
  public void testSwitchExpression() {
    inspectionFixture.get().doTest("""
             class Main {
                 int test(int i) {
                     int j = switch(i) {
                         default -> /*Result of assignment expression used*/i = 2/**/;
                     };
                     return j+i;
                 }
             }""");
  }

}