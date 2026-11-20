// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.initialization;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class NonThreadSafeLazyInitializationInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new NonThreadSafeLazyInitializationInspection());

  @Test
  public void testNonThreadSafeLazyInitialization() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testNestedAssignment() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Test
  public void testLocalVariableReferenced() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Test
  public void testInstanceVariableReferenced() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Test
  public void testQualifiedInstanceMethod() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Test
  public void testStaticVariableReferenced() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }

  @Test
  public void testNormal() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("introduce.holder.class.quickfix"));
  }
}
