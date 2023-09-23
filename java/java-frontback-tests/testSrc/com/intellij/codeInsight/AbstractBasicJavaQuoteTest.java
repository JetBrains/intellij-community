package com.intellij.codeInsight;

import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public abstract class AbstractBasicJavaQuoteTest extends LightPlatformCodeInsightTestCase {
  public void testDouble1() { doTest("<caret>", "\"<caret>\""); }

  public void testDouble2() { doTest("<caret>\"\"", "\"<caret>\"\"\""); }

  public void testDoubleClosing() { doTest("\"<caret>\"", "\"\"<caret>"); }

  public void testDoubleClosingWithText() { doTest("\"text<caret>\"", "\"text\"<caret>"); }

  public void testDoubleEscape() { doTest(" \"\\<caret>\" ", " \"\\\"<caret>\" "); }

  public void testDoubleEscapeClosing() { doTest(" \"\\\\<caret>\" ", " \"\\\\\"<caret> "); }

  public void testBeforeIdentifier() { doTest("foo(<caret>a);", "foo(\"<caret>a);"); }

  public void testDoubleInString() { doTest("\"Hello\"<caret> world\";", "\"Hello\"\"<caret> world\";"); }

  public void testBeforeStringWithEscape() { doTest("foo(P + <caret>\"\\n\" + \"xxx\" + E)", "foo(P + \"<caret>\"\"\\n\" + \"xxx\" + E)"); }

  public void testSingleInString() {
    doTest(" \"<caret>\" ", " \"'<caret>\" ", '\'');
  }

  public void testSingleInComment() {
    doTest("/* <caret> */", "/* '<caret> */", '\'');
  }

  public void testSingleInStringAfterEscape() {
    doTest(" split(text, '\\<caret>); ", " split(text, '\\'<caret>); ", '\'');
  }


  public void testDoubleQuoteInTextBlock() { doTest(" \"\"\" <caret> \"\"\" ", " \"\"\" \"<caret> \"\"\" "); }

  public void testSingleQuoteInTextBlock() {
    doTest(" \"\"\" <caret> \"\"\" ", " \"\"\" '<caret> \"\"\" ", '\'');
  }

  public void testTextBlockClosing() {
    doTest(" \"\"\".<caret>\"\"\" ", " \"\"\".\"<caret>\"\" ");
    doTest(" \"\"\".\"<caret>\"\" ", " \"\"\".\"\"<caret>\" ");
    doTest(" \"\"\".\"\"<caret>\" ", " \"\"\".\"\"\"<caret> ");
  }

  private void doTest(String before, String after, char c) {
    doFileTest("class C {{\n  " + before + "\n}}",
               "class C {{\n  " + after + "\n}}",
               c);
  }

  protected void doTest(String before, String after) {
    doTest(before, after, '\"');
  }

  protected void doFileTest(String completeBefore, String expectedResult, char c) {
    configureFromFileText("a.java", completeBefore);
    TypedAction.getInstance().actionPerformed(getEditor(), c, ((EditorEx)getEditor()).getDataContext());
    checkResultByText(expectedResult);
  }

  @SuppressWarnings("SameParameterValue")
  protected void doFileTest(String completeBefore, String expectedResult) {
    doFileTest(completeBefore, expectedResult, '\"');
  }
}
