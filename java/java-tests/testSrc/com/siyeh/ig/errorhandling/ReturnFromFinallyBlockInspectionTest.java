package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ReturnFromFinallyBlockInspectionTest extends LightJavaInspectionTestCase {

  public void testReturnFromFinallyBlock() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ReturnFromFinallyBlockInspection();
  }
}
