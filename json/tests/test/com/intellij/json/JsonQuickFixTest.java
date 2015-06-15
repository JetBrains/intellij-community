package com.intellij.json;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonQuickFixTest extends JsonTestCase {

  protected void doTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass, @NotNull String hint) {
    final String testFileName = "quickfix/" + getTestName(false);
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFile(testFileName + ".json");
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.getAvailableIntention(hint);
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.json", true);
  }

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

  public void testJSON2() {
    doTest(JsonStandardComplianceInspection.class, JsonBundle.message("quickfix.add.double.quotes.desc"));
  }

  public void testJSON3() {
    doTest(JsonStandardComplianceInspection.class, JsonBundle.message("quickfix.add.double.quotes.desc"));
  }
}
