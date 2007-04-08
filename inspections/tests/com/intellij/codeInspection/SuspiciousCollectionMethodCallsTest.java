package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.InspectionTestCase;

public class SuspiciousCollectionMethodCallsTest extends InspectionTestCase {
  protected void setUp() throws Exception {
    super.setUp();
    myPsiManager.setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
  }


  private void doTest() throws Exception {
    final LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(new SuspiciousCollectionsMethodCallsInspection());
    doTest("suspiciousCalls/" + getTestName(false),
           tool, "java 1.5");
  }

  public void testWildcardCapture() throws Exception { doTest(); }
}