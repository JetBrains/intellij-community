package com.intellij.json;

import com.intellij.json.codeinsight.JsonStandardComplianceInspection;

/**
 * @author Mikhail Golubev
 */
public class JsonHighlightingTest extends JsonTestCase {

  private void doTest() {
    doTestHighlighting(true, true, true);
  }

  private long doTestHighlighting(boolean checkInfo, boolean checkWeakWarning, boolean checkWarning) {
    return myFixture.testHighlighting(checkWarning, checkInfo, checkWeakWarning, "/highlighting/" + getTestName(false) + ".json");
  }

  private void enableStandardComplianceInspection(boolean checkComments) {
    final JsonStandardComplianceInspection inspection = new JsonStandardComplianceInspection();
    inspection.myWarnAboutComments = checkComments;
    myFixture.enableInspections(inspection);
  }

  public void testStringLiterals() {
    doTest();
  }

  public void testComplianceProblemsTopLevelValue() {
    enableStandardComplianceInspection(true);
    doTest();
  }

  public void testComplianceProblems() {
    enableStandardComplianceInspection(true);
    doTest();
  }

  // Moved from JavaScript

  public void testJSON_with_comment() throws Exception {
    enableStandardComplianceInspection(false);
    doTest();
  }

  public void testJSON() throws Exception {
    enableStandardComplianceInspection(true);
    doTest();
  }

  // TODO: JSON semantic highlighting?
  /*
  public void testSemanticHighlighting() throws Exception {
    doTest();
  }
  */

}
