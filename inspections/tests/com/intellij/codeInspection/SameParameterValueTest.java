package com.intellij.codeInspection;

import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.testFramework.InspectionTestCase;

public class SameParameterValueTest extends InspectionTestCase {

  private SameParameterValueInspection myTool = new SameParameterValueInspection();

  private String getTestDir() {
    return "sameParameterValue/" + getTestName(false);
  }

  public void testEntryPoint() throws Exception {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testWithoutDeadCode() throws Exception {
    doTest(getTestDir(), myTool, false, false);
  }

  public void testVarargs() throws Exception {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testSimpleVararg() throws Exception {
    doTest(getTestDir(), myTool, false, true);
  }
}