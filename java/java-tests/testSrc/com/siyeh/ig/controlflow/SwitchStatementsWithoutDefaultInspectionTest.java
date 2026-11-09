// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class SwitchStatementsWithoutDefaultInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

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