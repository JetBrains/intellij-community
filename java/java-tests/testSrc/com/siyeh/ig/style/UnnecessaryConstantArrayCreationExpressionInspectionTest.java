package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryConstantArrayCreationExpressionInspectionTest extends LightJavaInspectionTestCase {

  public void testUnnecessaryConstantArrayCreationExpression() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryConstantArrayCreationExpressionInspection();
  }
}