package com.intellij.json;

/**
 * @author Mikhail Golubev
 */
public class JsonHighlightingTest extends JsonTestCase {

  private void doTest() {
    final String TEST_PATH = "/highlighting/";
    myFixture.testHighlighting(true, true, false, TEST_PATH + getTestName(false) + ".json");
  }

  public void testStringLiterals() {
    doTest();
  }

  public void testCompliance() {
    doTest();
  }

  public void testComplianceTopLevelValues() {
    doTest();
  }
}
