package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class SameParameterValueLocalInspectionKtTest extends JavaInspectionTestCase {
  private final SameParameterValueInspection myGlobalTool = new SameParameterValueInspection();
  private LocalInspectionTool myTool = myGlobalTool.getSharedLocalInspectionTool();

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
    myTool = null;
    super.tearDown();
    myGlobalTool.highestModifier = PsiModifier.PROTECTED;
  }

  public void testEntryPoint() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testMethodWithSuper() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testVarargs() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testNegativeDouble() {
    doTest(getGlobalTestDir(), myTool);
  }
}
