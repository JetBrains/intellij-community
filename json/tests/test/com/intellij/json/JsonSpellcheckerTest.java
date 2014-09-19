package com.intellij.json;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;

/**
 * @author Mikhail Golubev
 */
public class JsonSpellcheckerTest extends JsonTestCase {

  private void doTest() {
    myFixture.enableInspections(SpellCheckingInspection.class);
    myFixture.configureByFile("/spellchecker/" + getTestName(false) + ".json");
    myFixture.checkHighlighting(true, false, true);
  }

  public void testEscapeAwareness() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }
}
