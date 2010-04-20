package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.InspectionTestCase;

public class NumericOverflowTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("numericOverflow/" + getTestName(true), new NumericOverflowInspection());
  }


  public void testSimple() throws Exception {
    doTest();
  }
}
