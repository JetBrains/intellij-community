package com.intellij.codeInspection;

import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;

/**
 * @author max
 */
public class EmptyMethodTest extends InspectionTestCase {
  private void doTest() throws Exception {
    final EmptyMethodInspection tool = new EmptyMethodInspection();
    tool.initialize(getManager());
    doTest("emptyMethod/" + getTestName(false), tool);
  }

  public void testsuperCall() throws Exception {
    doTest();
  }

  public void testexternalOverride() throws Exception {
    doTest();
  }

  public void testSCR8321() throws Exception {
    doTest();
  }
}
