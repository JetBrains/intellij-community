package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection;
import com.intellij.testFramework.InspectionTestCase;

public class SameReturnValueInspectionKtTest extends InspectionTestCase {
  private final SameReturnValueInspection myGlobalTool = new SameReturnValueInspection();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm";
  }

  private String getGlobalTestDir() {
    return "sameReturnValue/" + getTestName(true);
  }

  public void testJava() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

  public void testKotlin() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

  public void testMixed() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }
}
