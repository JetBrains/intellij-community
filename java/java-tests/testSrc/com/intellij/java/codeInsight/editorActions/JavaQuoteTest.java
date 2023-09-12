package com.intellij.java.codeInsight.editorActions;

import com.intellij.codeInsight.AbstractBasicJavaQuoteTest;

public class JavaQuoteTest extends AbstractBasicJavaQuoteTest {
  //requires formatter
  public void testTextBlock() { doTest("\"\"<caret> ", "  \"\"\"\n            <caret>\"\"\" "); }

  //requires formatter
  public void testPrecedingTextBlock() {
    doTest("f(\"\"<caret> + \"\"\"\n  .\"\"\")", "f(\"\"\"\n          <caret>\"\"\" + \"\"\"\n  .\"\"\")");
  }

  //basic highlighter doesn't support document data out-of-box
  public void testJavadocSnippetAttributeDouble() {
    doFileTest("/**\n* {@snippet <caret> :}\n*/\nclass A {\n}", "/**\n* {@snippet \"<caret>\" :}\n*/\nclass A {\n}");
  }

  //basic highlighter doesn't support document data out-of-box
  public void testJavadocSnippetAttributeSingle() {
    doFileTest("/**\n* {@snippet <caret> :}\n*/\nclass A {\n}",
               "/**\n* {@snippet '<caret>' :}\n*/\nclass A {\n}", '\'');
  }
}
