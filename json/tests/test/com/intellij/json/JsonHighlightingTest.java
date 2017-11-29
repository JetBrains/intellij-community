package com.intellij.json;

import com.intellij.json.codeinsight.JsonDuplicatePropertyKeysInspection;
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

  private void enableStandardComplianceInspection(boolean checkComments, boolean checkTopLevelValues) {
    final JsonStandardComplianceInspection inspection = new JsonStandardComplianceInspection();
    inspection.myWarnAboutComments = checkComments;
    inspection.myWarnAboutMultipleTopLevelValues = checkTopLevelValues;
    myFixture.enableInspections(inspection);
  }

  public void testStringLiterals() {
    doTest();
  }

  // IDEA-134372
  public void testComplianceProblemsLiteralTopLevelValueIsAllowed() {
    enableStandardComplianceInspection(true, true);
    doTest();
  }

  // WEB-16009
  public void testComplianceProblemsMultipleTopLevelValuesAllowed() {
    enableStandardComplianceInspection(true, false);
  }

  public void testComplianceProblems() {
    enableStandardComplianceInspection(true, true);
    doTestHighlighting(false, true, true);
  }

  public void testDuplicatePropertyKeys() {
    myFixture.enableInspections(JsonDuplicatePropertyKeysInspection.class);
    doTestHighlighting(false, true, true);
  }

  // WEB-13600
  public void testIncompleteFloatingPointLiteralsWithExponent() {
    doTestHighlighting(false, false, false);
  }

  // Moved from JavaScript

  public void testJSON_with_comment() {
    enableStandardComplianceInspection(false, true);
    doTestHighlighting(false, true, true);
  }

  public void testJSON() {
    enableStandardComplianceInspection(true, true);
    doTestHighlighting(false, true, true);
  }

  public void testSemanticHighlighting() {
    // WEB-11239
    doTest();
  }
}
