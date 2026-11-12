package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.codeinsight.JsonDuplicatePropertyKeysInspection;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.json.JsonHighlightingTestFixtureKt.jsonHighlightingFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonHighlightingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonHighlightingTestFixture> highlightingFixture = jsonHighlightingFixture(codeInsightFixture, "json");

  private CodeInsightTestFixture myFixture;
  private JsonHighlightingTestFixture myHighlightingFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    myHighlightingFixture = highlightingFixture.get();
  }

  private void enableStandardComplianceInspection(boolean checkComments, boolean checkTopLevelValues) {
    final JsonStandardComplianceInspection inspection = new JsonStandardComplianceInspection();
    inspection.myWarnAboutComments = checkComments;
    inspection.myWarnAboutMultipleTopLevelValues = checkTopLevelValues;
    myFixture.enableInspections(inspection);
  }

  @Test
  public void testStringLiterals() {
    myHighlightingFixture.doTest();
  }

  // IDEA-134372
  @Test
  public void testComplianceProblemsLiteralTopLevelValueIsAllowed() {
    enableStandardComplianceInspection(true, true);
    myHighlightingFixture.doTest();
  }

  // WEB-16009
  @Test
  public void testComplianceProblemsMultipleTopLevelValuesAllowed() {
    enableStandardComplianceInspection(true, false);
  }

  @Test
  public void testComplianceProblems() {
    enableStandardComplianceInspection(true, true);
    myHighlightingFixture.doTestHighlighting(false, true, true);
  }

  @Test
  public void testJsonLinesComplianceProblems() {
    enableStandardComplianceInspection(true, true);
    myHighlightingFixture.doTestHighlightingForJsonLines(false, true, true);
  }

  @Test
  public void testJsonLinesEmptyFile() {
    enableStandardComplianceInspection(true, true);
    myHighlightingFixture.doTestHighlightingForJsonLines(false, true, true);
  }

  @Test
  public void testEmptyFile() {
    enableStandardComplianceInspection(true, true);
    myHighlightingFixture.doTestHighlighting(false, true, true);
  }

  @Test
  public void testDuplicatePropertyKeys() {
    myFixture.enableInspections(JsonDuplicatePropertyKeysInspection.class);
    myHighlightingFixture.doTestHighlighting(false, true, true);
  }

  // WEB-13600
  @Test
  public void testIncompleteFloatingPointLiteralsWithExponent() {
    myHighlightingFixture.doTestHighlighting(false, false, false);
  }

  // Moved from JavaScript

  @Test
  public void testJSON_with_comment() {
    enableStandardComplianceInspection(false, true);
    myHighlightingFixture.doTestHighlighting(false, true, true);
  }

  @Test
  public void testJSON() {
    enableStandardComplianceInspection(true, true);
    myHighlightingFixture.doTestHighlighting(false, true, true);
  }

  @Test
  public void testTabInString() {
    enableStandardComplianceInspection(true, true);
    myHighlightingFixture.doTestHighlighting(false, true, true);
  }

  @Test
  public void testSemanticHighlighting() {
    // WEB-11239
    myHighlightingFixture.doTest();
  }

  @Test
  public void testWebUrls() {
    myHighlightingFixture.doTest();
  }

  @Test
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
