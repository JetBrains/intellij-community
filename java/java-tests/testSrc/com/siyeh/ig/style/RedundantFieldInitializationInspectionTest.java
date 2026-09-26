package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class RedundantFieldInitializationInspectionTest extends LightJavaInspectionTestCase {

  public void testRedundantFieldInitialization() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantFieldInitializationInspection();
  }
}