package com.intellij.json;

import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonCommenterTest extends JsonTestCase {

  private void doTest(@NotNull String actionId) {
    myFixture.configureByFile("commenter/" + getTestName(false) + ".json");
    myFixture.performEditorAction(actionId);
    myFixture.checkResultByFile("commenter/" + getTestName(false) + "_after.json", true);
  }

  public void testLineComment() {
    doTest(IdeActions.ACTION_COMMENT_LINE);
  }

  public void testLineComment2() {
    doTest(IdeActions.ACTION_COMMENT_LINE);
  }

  public void testLineComment3() {
    doTest(IdeActions.ACTION_COMMENT_LINE);
  }

  public void testBlockComment() {
    doTest(IdeActions.ACTION_COMMENT_BLOCK);
  }
}
