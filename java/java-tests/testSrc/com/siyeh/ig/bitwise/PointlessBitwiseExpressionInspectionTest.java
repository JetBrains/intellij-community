// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bitwise;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PointlessBitwiseExpressionInspectionTest extends LightJavaInspectionTestCase {

  public void testPointlessBitwiseExpression() {
    doTest();
    checkQuickFixAll();
  }

  public void testPointlessBitwiseExpressionNoConstants() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    PointlessBitwiseExpressionInspection inspection = new PointlessBitwiseExpressionInspection();
    inspection.m_ignoreExpressionsContainingConstants = getTestName(false).endsWith("NoConstants");
    return inspection;
  }
}