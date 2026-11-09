// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.assignment;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class NestedAssignmentInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new NestedAssignmentInspection());

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