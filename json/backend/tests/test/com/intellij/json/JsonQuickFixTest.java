package com.intellij.json;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/json/backend/tests/testData")
public class JsonQuickFixTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<String> testNameFixture = testNameFixture(false);

  private CodeInsightTestFixture myFixture;
  private String myTestName;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    myTestName = testNameFixture.get();
  }

  private void doTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass, @NotNull String hint) {
    final String testFileName = "quickfix/" + myTestName;
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".json");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.getAvailableIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.json", true);
  }

  @Test
  public void testWrapInDoubleQuotes() {
    checkWrapInDoubleQuotes("{n<caret>ull: false}", "{\"null\": false}");
    checkWrapInDoubleQuotes("{t<caret>rue: false}", "{\"true\": false}");
    checkWrapInDoubleQuotes("{4<caret>2: false}", "{\"42\": false}");
    checkWrapInDoubleQuotes("{fo<caret>o: false}", "{\"foo\": false}");
    checkWrapInDoubleQuotes("{'fo<caret>o': false}", "{\"foo\": false}");
    checkWrapInDoubleQuotes("'foo\\\"", "\"foo\\\"\"");
    checkWrapInDoubleQuotes("{\"foo\": b<caret>ar}", "{\"foo\": \"bar\"}");
    checkWrapInDoubleQuotes("{\"foo\": 'b<caret>ar'}", "{\"foo\": \"bar\"}");
    checkWrapInDoubleQuotes("'foo\\n\\'\"\\\\\\\"bar", "\"foo\\n'\\\"\\\\\\\"bar\"");
  }

  private void checkWrapInDoubleQuotes(@NotNull String before, @NotNull String after) {
    myFixture.configureByText(JsonFileType.INSTANCE, before);
    myFixture.enableInspections(JsonStandardComplianceInspection.class);
    final IntentionAction intentionAction = myFixture.getAvailableIntention(JsonBundle.message("quickfix.add.double.quotes.desc"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResult(after);
  }

  // Moved from JavaScript

  @Test
  public void testJSON2() {
    doTest(JsonStandardComplianceInspection.class, JsonBundle.message("quickfix.add.double.quotes.desc"));
  }

  @Test
  public void testJSON3() {
    doTest(JsonStandardComplianceInspection.class, JsonBundle.message("quickfix.add.double.quotes.desc"));
  }
}
