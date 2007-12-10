package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.InspectionTestCase;

public class SuspiciousCollectionMethodCallsTest extends InspectionTestCase {
  private SuspiciousCollectionsMethodCallsInspection myTool = new SuspiciousCollectionsMethodCallsInspection();

  protected void setUp() throws Exception {
    super.setUp();
    myJavaFacade.setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
  }


  private void doTest() throws Exception {
    final LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(myTool);
    doTest("suspiciousCalls/" + getTestName(false), tool, "java 1.5");
  }

  public void testWildcardCapture() throws Exception { doTest(); }
  public void testIgnoreConvertible() throws Exception {
    myTool.REPORT_CONVERTIBLE_METHOD_CALLS = false;
    doTest();
  }
}