package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class EmptyMethodTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean checkRange) throws Exception {
    final EmptyMethodInspection tool = new EmptyMethodInspection();
    doTest("emptyMethod/" + getTestName(true), tool, checkRange);
  }

  public void testSuperCall() throws Exception {
    doTest();
  }

  public void testSuperCallByRange() throws Exception {
    doTest(true);
  }

  public void testExternalOverride() throws Exception {
    doTest();
  }

  public void testSCR8321() throws Exception {
    doTest();
  }

  public void testInAnonymous() throws Exception {
    doTest(true);
  }

  public void testSuperFromAnotherPackageCall() throws Exception {
    doTest();
  }

  public void testSuperWithoutSync() throws Exception {
    doTest();
  }

  public void testEmptyMethodsHierarchy() throws Exception {
    doTest();
  }
}
