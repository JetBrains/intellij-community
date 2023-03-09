package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection;
import com.intellij.testFramework.JavaInspectionTestCase;

public class LocalSameReturnValueInspectionTest extends JavaInspectionTestCase {
  private final SameReturnValueInspection myGlobalTool = new SameReturnValueInspection();
  private LocalInspectionTool myTool = myGlobalTool.getSharedLocalInspectionTool();


  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getGlobalTestDir() {
    return "sameReturnValue/" + getTestName(true);
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }


  public void testJava() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testKotlin() {
    doTest(getGlobalTestDir(), myTool);
  }
}
