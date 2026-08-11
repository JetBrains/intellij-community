// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PointlessBooleanExpressionInspectionTest extends LightJavaInspectionTestCase {

  public void testBoxedEqualsCallBoxed() { doTest(); }
  public void testBoxedEqualsCallBoxedNullable() { doTest(); }
  public void testBoxedEqualsCallBoxedNullableReturnValue() { doTest(); }
  public void testBoxedEqualsCallObject() { doTest(); }
  public void testBoxedEqualsCallPrimitive() { doTest(); }
  public void testBoxedEqualsCallPrimitiveParenthesized() { doTest(); }
  public void testBoxedEqualsSignBoxed() { doTest(); }
  public void testBoxedEqualsSignBoxedNullable() { doTest(); }
  public void testBoxedEqualsSignBoxedNullableReturnValue() { doTest(); }
  public void testBoxedEqualsSignPrimitive() { doTest(); }
  public void testBoxedEqualsSignPrimitive2() { doTest(); }
  public void testPointlessBooleanExpression() { doTest(); }
  public void testRegression() { doTest(); }
  public void testFlexibleConstructorBodiesNotAvailable() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, this::doTest); }

  @Override
  protected @NotNull InspectionProfileEntry getInspection() {
    final PointlessBooleanExpressionInspection inspection = new PointlessBooleanExpressionInspection();
    inspection.m_ignoreExpressionsContainingConstants = true;
    return inspection;
  }
}
