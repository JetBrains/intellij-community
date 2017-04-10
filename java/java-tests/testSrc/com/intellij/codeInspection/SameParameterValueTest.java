package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.InspectionTestCase;

public class SameParameterValueTest extends InspectionTestCase {
  private SameParameterValueInspection myTool = new SameParameterValueInspection();

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testEntryPoint() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testWithoutDeadCode() {
    String previous = myTool.highestModifier;
    myTool.highestModifier = PsiModifier.PUBLIC;
    try {
      doTest(getTestDir(), myTool, false, false);
    } finally {
      myTool.highestModifier = previous;
    }
  }

  public void testVarargs() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testSimpleVararg() {
    doTest(getTestDir(), myTool, false, true);
  }
  
  public void testMethodWithSuper() {
    String previous = myTool.highestModifier;
    myTool.highestModifier = PsiModifier.PUBLIC;
    try {
      doTest(getTestDir(), myTool, false, true);
    } finally {
      myTool.highestModifier = previous;
    }
  }

  public void testNotReportedDueToHighVisibility() {
    doTest(getTestDir(), myTool, false, false);
  }
}
