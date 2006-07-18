package com.intellij.codeInspection;

import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class EmptyMethodTest extends InspectionTestCase {

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean checkRange) throws Exception {
    final EmptyMethodInspection tool = new EmptyMethodInspection();
    doTest("emptyMethod/" + getTestName(false), tool, checkRange);
  }

  public void testsuperCall() throws Exception {
    doTest();
  }

  public void testsuperCallByRange() throws Exception {
    doTest(true);
  }

  public void testexternalOverride() throws Exception {
    doTest();
  }

  public void testSCR8321() throws Exception {
    doTest();
  }
}
