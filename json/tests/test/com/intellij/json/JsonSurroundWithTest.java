package com.intellij.json;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.json.surroundWith.JsonWithObjectLiteralSurrounder;
import com.intellij.openapi.command.WriteCommandAction;

/**
 * @author Mikhail Golubev
 */
public class JsonSurroundWithTest extends JsonTestCase {
  private void doTest() {
    myFixture.configureByFile("/surround/" + getTestName(false) + ".json");
    new WriteCommandAction.Simple(myFixture.getProject()) {
      @Override
      protected void run() {
        SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), new JsonWithObjectLiteralSurrounder());
      }
    }.execute();
    myFixture.checkResultByFile("/surround/" + getTestName(false) + "_after.json", true);
  }

  public void testSingleValue() {
    doTest();
  }

  public void testSingleProperty() {
    doTest();
  }

  public void testMultipleProperties() {
    doTest();
  }

  public void testCannotSurroundPropertyKey() {
    doTest();
  }

  // Moved from JavaScript


  public void testObjectLiteral() {
    doTest();
  }
}
