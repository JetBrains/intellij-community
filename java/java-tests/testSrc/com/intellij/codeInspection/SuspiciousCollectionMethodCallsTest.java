package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection;
import com.intellij.testFramework.InspectionTestCase;

public class SuspiciousCollectionMethodCallsTest extends InspectionTestCase {
  private final SuspiciousCollectionsMethodCallsInspection myTool = new SuspiciousCollectionsMethodCallsInspection();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    final LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(myTool);
    doTest("suspiciousCalls/" + getTestName(false), tool, "java 1.5");
  }

  public void testWildcardCapture() throws Exception { doTest(); }
  public void testWildcard() throws Exception { doTest(); }
  public void testIgnoreConvertible() throws Exception {
    myTool.REPORT_CONVERTIBLE_METHOD_CALLS = false;
    doTest();
  }
}
