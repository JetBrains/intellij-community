// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.methodmetrics;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class MultipleReturnPointsPerMethodInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_20);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new MultipleReturnPointsPerMethodInspection());

  @Test
  public void testMultipleReturnPointsPerMethod() {
    inspectionFixture.get().doTest();
  }
}