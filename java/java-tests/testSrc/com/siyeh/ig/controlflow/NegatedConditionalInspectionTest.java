// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Roland Illig
 */
@TestApplication
public class NegatedConditionalInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  private final NegatedConditionalInspection myInspection = new NegatedConditionalInspection();

  @Test
  public void testNegatedConditionalNullAndZeroAllowed() {
    myInspection.m_ignoreNegatedNullComparison = true;
    myInspection.m_ignoreNegatedZeroComparison = true;

    inspectionFixture.get().doTest();
  }

  @Test
  public void testNegatedConditionalNullAndZeroDisallowed() {
    myInspection.m_ignoreNegatedNullComparison = false;
    myInspection.m_ignoreNegatedZeroComparison = false;

    inspectionFixture.get().doTest();
  }

  @Test
  public void testNegatedConditionalNullAllowed() {
    myInspection.m_ignoreNegatedNullComparison = true;
    myInspection.m_ignoreNegatedZeroComparison = false;

    inspectionFixture.get().doTest();
  }

  @Nullable
  private InspectionProfileEntry getInspection() {
    return myInspection;
  }
}