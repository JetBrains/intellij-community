package com.intellij.codeInspection;

import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author cdr
 */
public class RedundantArrayForVarargsCallInspectionTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("redundantArrayForVarargs/" + getTestName(true), new LocalInspectionToolWrapper(new RedundantArrayForVarargsCallInspection()),"java 1.5");
  }

  public void testIDEADEV15215() throws Exception { doTest(); }
  public void testNestedArray() throws Exception { doTest(); }
}
