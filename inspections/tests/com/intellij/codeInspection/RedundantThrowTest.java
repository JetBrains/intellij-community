package com.intellij.codeInspection;

import com.intellij.codeInspection.unneededThrows.RedundantThrows;
import com.intellij.testFramework.InspectionTestCase;


public class RedundantThrowTest extends InspectionTestCase {
  private void doTest() throws Exception {
    final RedundantThrows tool = new RedundantThrows();
    doTest("redundantThrow/" + getTestName(false), tool);
  }

  public void testSCR8322() throws Exception { doTest(); }

  public void testSCR6858() throws Exception { doTest(); }

  public void testSCR14543() throws Exception { doTest(); }
}
