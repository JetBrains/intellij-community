package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class StringConcatenationInsideStringBufferAppendInspectionTest extends LightJavaInspectionTestCase {

  public void testStringConcatenationInsideStringBufferAppend() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringConcatenationInsideStringBufferAppendInspection();
  }
}