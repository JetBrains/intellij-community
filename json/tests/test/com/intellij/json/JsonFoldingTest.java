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
}
