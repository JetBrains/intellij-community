package com.intellij.json;

import com.intellij.testFramework.fixtures.CodeInsightTestUtil;

/**
 * @author Mikhail Golubev
 */
public class JsonWordSelectionTest extends JsonTestCase {

  private void doTest() {
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, "selectWord/" + getTestName(false), "json");
  }

  public void testEscapeAwareness() {
    doTest();
  }
}
