package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class StringCompareToEqualityInspectionTest extends LightJavaInspectionTestCase {

  public void testStringCompareToEquality() {
    doTest();
  }

  public void testStringCompareToEqualityFix() {
    doTest();
    checkQuickFix("Replace with 'equals()'");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringCompareToEqualityInspection();
  }
}
