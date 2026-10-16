package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class NanoTimeMagnitudeComparisonInspectionTest extends LightJavaInspectionTestCase {

  public void testNanoTimeMagnitudeComparison() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new NanoTimeMagnitudeComparisonInspection();
  }
}
