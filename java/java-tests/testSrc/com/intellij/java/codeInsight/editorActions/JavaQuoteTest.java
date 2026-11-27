// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AbstractBasicJavaQuoteTest;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class JavaQuoteTest extends AbstractBasicJavaQuoteTest {
  //requires formatter
  @Test
  public void testTextBlock() {
    doTest("\"\"<caret> ",
           "  \"\"\"\n            <caret>\"\"\" ");
  }
  
  @Test
  public void testTextBlockFormatting() {
    JavaCodeStyleSettings settings = CodeStyle.getSettings(getFixture().getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    settings.STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true;
    doTest("\"\"<caret> ",
           "  \"\"\"\n            <caret>\"\"\" ");
  }

  //requires formatter
  @Test
  public void testPrecedingTextBlock() {
    doTest("f(\"\"<caret> + \"\"\"\n  .\"\"\")",
           "f(\"\"\"\n          <caret>\"\"\" + \"\"\"\n  .\"\"\")");
  }

  //requires formatter
  @Test
  public void testTextBlockBeforeTemplate() {
    IdeaTestUtil.withLevel(getFixture().getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
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
  @Test
  public void testJavadocSnippetAttributeDouble() {
    doFileTest("/**\n* {@snippet <caret> :}\n*/\nclass A {\n}", "/**\n* {@snippet \"<caret>\" :}\n*/\nclass A {\n}");
  }

  //basic highlighter doesn't support document data out-of-box
  @Test
  public void testJavadocSnippetAttributeSingle() {
    doFileTest("/**\n* {@snippet <caret> :}\n*/\nclass A {\n}",
               "/**\n* {@snippet '<caret>' :}\n*/\nclass A {\n}", '\'');
  }

  @Test
  public void testJavadocMarkdownQuotes(){
    doFileTest("/// <caret>", "/// [<caret>]", '[');
    doFileTest("/// <caret>", "/// (<caret>)", '(');
    doFileTest("/// <caret>", "/// `<caret>`", '`');
  }
}
