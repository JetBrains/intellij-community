package com.intellij.java.codeInsight.editorActions;

import com.intellij.codeInsight.AbstractBasicJavaQuoteTest;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class JavaQuoteTest extends AbstractBasicJavaQuoteTest {
  //requires formatter
  public void testTextBlock() { doTest("\"\"<caret> ", "  \"\"\"\n            <caret>\"\"\" "); }

  //requires formatter
  public void testPrecedingTextBlock() {
    doTest("f(\"\"<caret> + \"\"\"\n  .\"\"\")", "f(\"\"\"\n          <caret>\"\"\" + \"\"\"\n  .\"\"\")");
  }

  //requires formatter
  public void testTextBlockBeforeTemplate() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      doTest("""
            Integer i = 5;
        
            String s1 = ""<caret>
        
            String s = STR."\\{i}";""",
             """
            Integer i = 5;
                          
            String s1 = ""\"
                    ""\"
                          
            String s = STR."\\{i}";""");
    });
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

  public void testJavadocMarkdownQuotes(){
    doFileTest("/// <caret>", "/// [<caret>]", '[');
    doFileTest("/// <caret>", "/// (<caret>)", '(');
    doFileTest("/// <caret>", "/// `<caret>`", '`');
  }
}
