package com.intellij.json;

import com.intellij.json.codeinsight.JsonDuplicatePropertyKeysInspection;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;

/**
 * @author Mikhail Golubev
 */
public class JsonHighlightingTest extends JsonHighlightingTestBase {

  @Override
  protected String getExtension() {
    return "json";
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

  public void testJsonLinesComplianceProblems() {
    enableStandardComplianceInspection(true, true);
    doTestHighlightingForJsonLines(false, true, true);
  }

  public void testJsonLinesEmptyFile() {
    enableStandardComplianceInspection(true, true);
    doTestHighlightingForJsonLines(false, true, true);
  }

  public void testEmptyFile() {
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

  public void testTabInString() {
    enableStandardComplianceInspection(true, true);
    doTestHighlighting(false, true, true);
  }

  public void testSemanticHighlighting() {
    // WEB-11239
    doTest();
  }

  public void testWebUrls() {
    doTest();
  }

  public void testRainbow() {
    myFixture.testRainbow("test.json",
                          """
                            {
                              <rainbow color='ff000002'>"type"</rainbow>: <rainbow color='ff000002'>"object"</rainbow>,
                              <rainbow color='ff000002'>"properties"</rainbow>: <rainbow color='ff000002'>{</rainbow>
                                <rainbow color='ff000004'>"versionAsStringArray"</rainbow>: <rainbow color='ff000004'>{</rainbow>
                                  <rainbow color='ff000002'>"type"</rainbow>: <rainbow color='ff000002'>"object"</rainbow>,
                                  <rainbow color='ff000002'>"properties"</rainbow>: <rainbow color='ff000002'>{</rainbow>
                                    <rainbow color='ff000003'>"xxx"</rainbow>: <rainbow color='ff000003'>{</rainbow>
                                      <rainbow color='ff000002'>"type"</rainbow>: <rainbow color='ff000002'>"number"</rainbow>
                                    <rainbow color='ff000003'>}</rainbow>,
                                    <rainbow color='ff000004'>"yyy"</rainbow>: <rainbow color='ff000004'>{</rainbow>
                                      <rainbow color='ff000001'>"description"</rainbow>: <rainbow color='ff000001'>"qqq"</rainbow>,
                                      <rainbow color='ff000002'>"type"</rainbow>: <rainbow color='ff000002'>"string"</rainbow>
                                    <rainbow color='ff000004'>}</rainbow>,
                                    <rainbow color='ff000001'>"zzz"</rainbow>: <rainbow color='ff000001'>{</rainbow>
                                      <rainbow color='ff000002'>"type"</rainbow>: <rainbow color='ff000002'>"number"</rainbow>
                                    <rainbow color='ff000001'>}</rainbow>
                                  <rainbow color='ff000002'>}</rainbow>,
                                  <rainbow color='ff000001'>"description"</rainbow>: <rainbow color='ff000001'>"aaa"</rainbow>,
                                  <rainbow color='ff000003'>"required"</rainbow>: <rainbow color='ff000003'>[</rainbow><rainbow color='ff000003'>"xxx"</rainbow>, <rainbow color='ff000003'>"yyy"</rainbow>, <rainbow color='ff000003'>"zzz"</rainbow><rainbow color='ff000003'>]</rainbow>
                                <rainbow color='ff000004'>}</rainbow>
                              <rainbow color='ff000002'>}</rainbow>,
                              <rainbow color='ff000003'>"required"</rainbow>: <rainbow color='ff000003'>[</rainbow><rainbow color='ff000003'>"versionAsStringArray"</rainbow><rainbow color='ff000003'>]</rainbow>
                            }""", true, true);
  }
}
