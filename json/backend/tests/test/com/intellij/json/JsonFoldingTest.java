package com.intellij.json;

/**
 * @author Mikhail Golubev
 */
public class JsonFoldingTest extends JsonTestCase {
  private void doTest() {
    myFixture.testFolding(getTestDataPath() + "/folding/" + getTestName(false) + ".json");
  }


  public void testArrayFolding() {
    doTest();
  }

  public void testObjectFolding() {
    doTest();
  }

  public void testCommentaries() {
    doTest();
  }

  // Moved from JavaScript

  public void testObjectLiteral2() {
    doTest();
  }

  public void testObjectLiteral3() {
    doTest();
  }

  public void testObjectLiteral4() {
    doTest();
  }

}
