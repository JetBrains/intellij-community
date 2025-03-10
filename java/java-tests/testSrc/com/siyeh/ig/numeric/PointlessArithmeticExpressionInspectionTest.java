// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.openapi.util.text.StringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PointlessArithmeticExpressionInspectionTest extends LightJavaInspectionTestCase {

  public void testPointlessArithmeticExpression() { doTest(); }
  public void testPointlessArithmeticExpression_disable_m_ignoreExpressionsContainingConstants() { doTest(); }
  public void testNestedVersusSuper() { doTest(); }
  public void testComments() { doQuickFixTest(); }
  public void testCast() { doQuickFixTest(); }
  public void testCommentInsideCall() { doQuickFixTest(); }
  public void testNamedConstants() { doTest(); }
  public void testNamedConstants_disable_m_ignoreExpressionsContainingConstants() { doTest(); }

  private void doQuickFixTest() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    PointlessArithmeticExpressionInspection inspection = new PointlessArithmeticExpressionInspection();
    inspection.m_ignoreExpressionsContainingConstants = true;
    String option = StringUtil.substringAfter(getName(), "_");
    if (option != null) {
      boolean enableOption = !option.startsWith("disable");
      if (!enableOption) {
        option = StringUtil.substringAfter(option, "disable_");
      }
      new OptionAccessor.Default(inspection).setOption(option, false);
    }
    return inspection;
  }
}
