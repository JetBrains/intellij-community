package com.siyeh.ig.finalization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class FinalizeInspectionTest extends LightJavaInspectionTestCase {

  public void testFinalize() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new FinalizeInspection();
  }
}