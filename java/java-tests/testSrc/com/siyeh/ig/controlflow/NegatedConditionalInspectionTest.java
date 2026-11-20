// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Roland Illig
 */
@AnalysisHostTest
public class NegatedConditionalInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> getInspection());

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