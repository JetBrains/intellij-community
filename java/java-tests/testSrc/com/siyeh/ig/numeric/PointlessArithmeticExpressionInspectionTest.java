// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PointlessArithmeticExpressionInspectionTest extends LightJavaInspectionTestCase {

  public void testPointlessArithmeticExpression() { doTest(); }
  public void testNestedVersusSuper() { doTest(); }
  public void testComments() { doQuickFixTest(); }
  public void testCast() { doQuickFixTest(); }
  public void testCommentInsideCall() { doQuickFixTest(); }

  private void doQuickFixTest() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new PointlessArithmeticExpressionInspection();
  }
}