package com.intellij.json;

/**
 * @author Mikhail Golubev
 */
public class JsonRenameTest extends JsonTestCase {

  private void doTest(final String newName) {
    myFixture.configureByFile("rename/" + getTestName(false) + ".json");
    myFixture.renameElementAtCaret(newName);
    myFixture.checkResultByFile("rename/" + getTestName(false) + "_after.json");
  }

  public void testPropertyNameRequiresEscaping() {
    doTest("\"/\\\b\f\n\r\t\0\u001B'");
  }

  // Moved from JavaScript

  public void testDuplicateProperties() {
    doTest("aaa2");
  }

  public void testDuplicatePropertiesQuotedName() {
    doTest("\"aaa2\"");
  }

}
