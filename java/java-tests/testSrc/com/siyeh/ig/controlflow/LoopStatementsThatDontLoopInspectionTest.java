// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixtureByTestName;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class LoopStatementsThatDontLoopInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName));

  @Test
  public void testLoopStatementsThatDontLoop() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIgnoreForeach() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    final LoopStatementsThatDontLoopInspection inspection = new LoopStatementsThatDontLoopInspection();
    inspection.ignoreForeach = testName.equals("IgnoreForeach");
    return inspection;
  }
}
