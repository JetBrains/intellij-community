package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.InspectionsBundle;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.TrivialStringConcatenationInspection;

public class TrivialStringConcatenationFixTest extends IGQuickFixesTestCase {

  private String fixAll;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TrivialStringConcatenationInspection inspection = new TrivialStringConcatenationInspection();
    inspection.skipIfNecessary = false;
    if (getTestName(true).contains("SkipIfNecessary")) {
      inspection.skipIfNecessary = true;
    }
    myFixture.enableInspections(inspection);
    myRelativePath = "performance/trivial_string_concatenation";
    myDefaultHint = InspectionGadgetsBundle.message("string.replace.quickfix");
    fixAll = InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                       InspectionGadgetsBundle.message("trivial.string.concatenation.display.name"));
  }

  public void testParentheses() { doTest(); }
  public void testParentheses2() { doTest(); }
  public void testBinaryNull() { doTest(); }
  public void testAtTheEnd() { doTest(); }
  public void testSeveralEmptyString() {
    doTest(fixAll);
  }
  public void testStartComments() {
    doTest();
  }
  public void testMiddleComments() {
    doTest();
  }
  public void testEndComments() {
    doTest();
  }

  public void testNotSeenString() {
    doTest(fixAll);
  }
  public void testNotSeenString_SkipIfNecessary() {
    doTest(fixAll);
  }
}