package com.intellij.json;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.json.surroundWith.JsonWithArrayLiteralSurrounder;
import com.intellij.json.surroundWith.JsonWithObjectLiteralSurrounder;
import com.intellij.json.surroundWith.JsonWithQuotesSurrounder;
import com.intellij.lang.surroundWith.Surrounder;

/**
 * @author Mikhail Golubev
 */
public class JsonSurroundWithTest extends JsonTestCase {
  private void doTest(Surrounder surrounder) {
    myFixture.configureByFile("/surround/" + getTestName(false) + ".json");
    SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), surrounder);
    myFixture.checkResultByFile("/surround/" + getTestName(false) + "_after.json", true);
  }

  public void testSingleValue() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }

  public void testSingleProperty() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }

  public void testMultipleProperties() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }

  public void testCannotSurroundPropertyKey() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }

  public void testArrayLiteral() {
    doTest(new JsonWithArrayLiteralSurrounder());
  }

  public void testMultipleValuesIntoArray() {
    doTest(new JsonWithArrayLiteralSurrounder());
  }

  public void testQuotes() {
    doTest(new JsonWithQuotesSurrounder());
  }

  public void testMultipleValuesIntoString() {
    doTest(new JsonWithQuotesSurrounder());
  }

  // Moved from JavaScript

  public void testObjectLiteral() {
    doTest(new JsonWithObjectLiteralSurrounder());
  }
}
