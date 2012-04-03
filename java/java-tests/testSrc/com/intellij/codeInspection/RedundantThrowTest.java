package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unneededThrows.RedundantThrows;
import com.intellij.testFramework.InspectionTestCase;


public class RedundantThrowTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(boolean checkRange) throws Exception {
    final RedundantThrows tool = new RedundantThrows();
    doTest("redundantThrow/" + getTestName(false), tool, checkRange);
  }

  public void testSCR8322() throws Exception { doTest(); }

  public void testSCR6858() throws Exception { doTest(); }

  public void testSCR6858ByRange() throws Exception { doTest(true); }

  public void testSCR14543() throws Exception { doTest(); }
  public void testRemote() throws Exception { doTest(); }

  public void testEntryPoint() throws Exception {
    final RedundantThrows tool = new RedundantThrows();
    doTest("redundantThrow/" + getTestName(true), tool, false, true);
  }

  public void testinherited() throws Exception {
    doTest();
  }

  public void testimplicitSuper() throws Exception {
    doTest();
  }

  public void testselfCall() throws Exception {
    doTest();
  }

}
