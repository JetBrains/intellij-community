package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.InspectionTestCase;

public class SameParameterValueInspectionKtTest extends InspectionTestCase {
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
