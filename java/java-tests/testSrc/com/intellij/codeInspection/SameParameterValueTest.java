package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.testFramework.InspectionTestCase;

public class SameParameterValueTest extends InspectionTestCase {
  private final SameParameterValueInspection myTool = new SameParameterValueInspection();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  public void testEntryPoint() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testWithoutDeadCode() {
    doTest(getTestDir(), myTool, false, false);
  }

  public void testVarargs() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testSimpleVararg() {
    doTest(getTestDir(), myTool, false, true);
  }
  
  public void testMethodWithSuper() {
    doTest(getTestDir(), myTool, false, true);
  }
}
