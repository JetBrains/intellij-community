// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class SwitchStatementsWithoutDefaultInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> getInspection());

  private final SwitchStatementsWithoutDefaultInspection myInspection = new SwitchStatementsWithoutDefaultInspection();

  @Test
  public void testIgnoreExhaustiveSwitchStatementsTrue() {
    myInspection.m_ignoreFullyCoveredEnums = true;
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIgnoreExhaustiveSwitchStatementsFalse() {
    myInspection.m_ignoreFullyCoveredEnums = false;
    inspectionFixture.get().doTest();
  }

  @Test
  public void testBrokenRecords() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private InspectionProfileEntry getInspection() {
    return myInspection;
  }
}