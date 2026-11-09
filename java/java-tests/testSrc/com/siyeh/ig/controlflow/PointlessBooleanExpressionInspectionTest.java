// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class PointlessBooleanExpressionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testBoxedEqualsCallBoxed() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsCallBoxedNullable() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsCallBoxedNullableReturnValue() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsCallObject() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsCallPrimitive() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsCallPrimitiveParenthesized() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsSignBoxed() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsSignBoxedNullable() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsSignBoxedNullableReturnValue() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsSignPrimitive() { inspectionFixture.get().doTest(); }
  @Test
  public void testBoxedEqualsSignPrimitive2() { inspectionFixture.get().doTest(); }
  @Test
  public void testPointlessBooleanExpression() { inspectionFixture.get().doTest(); }
  @Test
  public void testRegression() { inspectionFixture.get().doTest(); }
  @Test
  public void testFlexibleConstructorBodiesNotAvailable() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture(); IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, inspectionFixture.get()::doTest); }

  private static @NotNull InspectionProfileEntry getInspection() {
    final PointlessBooleanExpressionInspection inspection = new PointlessBooleanExpressionInspection();
    inspection.m_ignoreExpressionsContainingConstants = true;
    return inspection;
  }
}
