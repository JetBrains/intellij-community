package com.intellij.json;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;

/**
 * @author Mikhail Golubev
 */
public class JsonSpellcheckerTest extends JsonTestCase {

  private void doTest() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByFile(getTestName(false) + ".json");
    myFixture.checkHighlighting(true, false, true);
  }

  public void testEscapeAwareness() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }

  // WEB-31894 EA-117068
  public void testAfterModificationOfStringLiteralWithEscaping() {
    myFixture.configureByFile(getTestName(false) + ".json");
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.checkHighlighting();
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    myFixture.doHighlighting();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/spellchecker";
  }
}
