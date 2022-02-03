package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class SameParameterValueInspectionKtTest11 extends JavaInspectionTestCase {
  private final SameParameterValueInspection myGlobalTool = new SameParameterValueInspection();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myGlobalTool.highestModifier = PsiModifier.PUBLIC;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm";
  }

  private String getGlobalTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myGlobalTool.highestModifier = PsiModifier.PROTECTED;
  }

  public void testEntryPoint() {
    doTest(getGlobalTestDir(), myGlobalTool, false, true);
  }

  public void testMethodWithSuper() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

  public void testVarargs() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

  public void testNegativeDouble() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }
}
