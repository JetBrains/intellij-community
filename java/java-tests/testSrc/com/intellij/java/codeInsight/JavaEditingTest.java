// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.AbstractBasicJavaEditingTest;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaEditingTest extends AbstractBasicJavaEditingTest {
  @Test
  public void testAutoWrapJavadoc() {
    myFixture.runTest(() -> {
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      mySettings.setDefaultRightMargin(31);
      doTest(JavaFileType.INSTANCE, 'c');
    });
  }

  @Test
  public void testCaretInsideTabAfterFormatting() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-69872

      String initial =
        """
          class Test {
           <caret>   void test() {
              }
          }""";
      myFixture.init(initial, JavaFileType.INSTANCE);

      final CommonCodeStyleSettings.IndentOptions options = mySettings.getIndentOptions(JavaFileType.INSTANCE);
      options.USE_TAB_CHARACTER = true;
      options.TAB_SIZE = 4;

      final EditorSettings editorSettings = myFixture.getEditor().getSettings();
      final boolean old = editorSettings.isCaretInsideTabs();
      editorSettings.setCaretInsideTabs(false);
      try {
        ApplicationManager.getApplication()
          .runWriteAction(() -> CodeStyleManager.getInstance(myFixture.getProject())
            .reformatText(myFixture.getFile(), 0, myFixture.getEditor().getDocument().getTextLength()));
      }
      finally {
        editorSettings.setCaretInsideTabs(old);
      }

      String expected =
        """
          class Test {
          \t<caret>void test() {
          \t}
          }""";
      myFixture.checkResultByText(expected);
    });
  }


  @Test
  public void testAutoWrapRightMargin() {
    myFixture.runTest(() -> {
      int oldValue = mySettings.getDefaultRightMargin();
      boolean oldMarginValue = mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
      mySettings.setDefaultRightMargin(80);
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      try {
        configureByFile("/codeInsight/editing/before" + getTestName(false) + ".java");
        for (int i = 0; i != 47; ++i) {
          myFixture.type(' ');
        }
        checkResultByFile("/codeInsight/editing/after" + getTestName(false) + ".java");
      }
      finally {
        mySettings.setDefaultRightMargin(oldValue);
        mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldMarginValue;
      }
    });
  }

  @Test
  public void testAutoWrapLanguageRightMargin() {
    myFixture.runTest(() -> {
      CommonCodeStyleSettings javaCommonSettings = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
      int oldRightMargin = javaCommonSettings.RIGHT_MARGIN;
      boolean oldShouldWrap = mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
      javaCommonSettings.RIGHT_MARGIN = 80;
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      try {
        configureByFile("/codeInsight/editing/beforeAutoWrapRightMargin.java");
        for (int i = 0; i != 47; ++i) {
          myFixture.type(' ');
        }
        checkResultByFile("/codeInsight/editing/afterAutoWrapRightMargin.java");
      }
      finally {
        javaCommonSettings.RIGHT_MARGIN = oldRightMargin;
        mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldShouldWrap;
      }
    });
  }

  @Test
  public void testAutoWrapStringLiteral() {
    myFixture.runTest(() -> {
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
      mySettings.setDefaultRightMargin(27);
      doTest(JavaFileType.INSTANCE, 'j');
    });
  }

  @Test
  public void testJavadocBraces() {
    myFixture.runTest(() -> {
      myFixture.configureFromFileText("a.java", "/** <caret> */");
      myFixture.type('{');
      myFixture.checkResultByText("/** {<caret>} */");
      myFixture.type('}');
      myFixture.checkResultByText("/** {}<caret> */");
    });
  }

  @Test
  public void testSmartIndentOnEnterWithinNonLastStatement() {
    myFixture.runTest(() -> {
      CodeInsightSettings.runWithTemporarySettings(settings -> {
        settings.SMART_INDENT_ON_ENTER = true;
        doTest(JavaFileType.INSTANCE, '\n');
        return null;
      });
    });
  }

  @Test
  public void testEmacsTabWithSelection() {
    myFixture.runTest(() -> {
      myFixture.configureFromFileText(getTestName(false) + ".java", """
        class Foo {
        <selection>int a;
        int b;<caret></selection>
        }""");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_EMACS_TAB);
      myFixture.checkResultByText("""
                          class Foo {
                              <selection>int a;
                              int b;<caret></selection>
                          }""");
    });
  }

  @Test
  public void testUnindentLineWithTabs() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-76050

      String initial =
        """
          class Test {
          \tvoid test() {
          \t\t<caret>
          \t}
          }""";
      myFixture.init(initial, JavaFileType.INSTANCE);
      CommonCodeStyleSettings.IndentOptions indentOptions =
        getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
      assertNotNull(indentOptions);
      boolean oldUseTabs = indentOptions.USE_TAB_CHARACTER;
      boolean oldUseSmartTabs = indentOptions.SMART_TABS;
      try {
        indentOptions.USE_TAB_CHARACTER = true;
        indentOptions.SMART_TABS = true;

        CodeStyleSettingsManager.getInstance(myFixture.getProject()).notifyCodeStyleSettingsChanged();

        myFixture.unindent();
        myFixture.checkResultByText(
          """
            class Test {
            \tvoid test() {
            \t<caret>
            \t}
            }"""
        );
      }
      finally {
        indentOptions.USE_TAB_CHARACTER = oldUseTabs;
        indentOptions.SMART_TABS = oldUseSmartTabs;
      }
    });
  }
}
