// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PointlessBooleanExpressionInspectionTest extends LightJavaInspectionTestCase {

  public void testBoxedEqualsCallBoxed() {
    doTest();
  }

  public void testBoxedEqualsCallBoxedNullable() {
    doTest();
  }

  public void testBoxedEqualsCallBoxedNullableReturnValue() {
    doTest();
  }

  public void testBoxedEqualsCallObject() {
    doTest();
  }

  public void testBoxedEqualsCallPrimitive() {
    doTest();
  }

  public void testBoxedEqualsCallPrimitiveParenthesized() {
    doTest();
  }

  public void testBoxedEqualsSignBoxed() {
    doTest();
  }

  public void testBoxedEqualsSignBoxedNullable() {
    doTest();
  }

  public void testBoxedEqualsSignBoxedNullableReturnValue() {
    doTest();
  }

  public void testBoxedEqualsSignPrimitive() {
    doTest();
  }

  public void testBoxedEqualsSignPrimitive2() {
    doTest();
  }

  public void testPointlessBooleanExpression() {
    doTest();
  }

  public void testRegression() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final PointlessBooleanExpressionInspection inspection = new PointlessBooleanExpressionInspection();
    inspection.m_ignoreExpressionsContainingConstants = true;
    return inspection;
  }
}
