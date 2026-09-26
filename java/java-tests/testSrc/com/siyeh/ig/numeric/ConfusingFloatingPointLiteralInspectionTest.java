package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ConfusingFloatingPointLiteralInspectionTest extends LightJavaInspectionTestCase {

  public void testConfusingFloatingPointLiteral() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ConfusingFloatingPointLiteralInspection();
  }
}