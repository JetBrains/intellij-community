package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ConstantValueVariableUseInspectionTest extends LightJavaInspectionTestCase {

  public void testConstantValueVariableUse() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ConstantValueVariableUseInspection();
  }
}