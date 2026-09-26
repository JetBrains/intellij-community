// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public class JavaEditingTest extends AbstractBasicJavaEditingTest {
  public void testAutoWrapJavadoc() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(31);
    doTest(JavaFileType.INSTANCE, 'c');
  }

  public void testCaretInsideTabAfterFormatting() {
    // Inspired by IDEA-69872

    String initial =
      """
        class Test {
         <caret>   void test() {
            }
        }""";
    init(initial, JavaFileType.INSTANCE);

    final CommonCodeStyleSettings.IndentOptions options = mySettings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.TAB_SIZE = 4;

    final EditorSettings editorSettings = getEditor().getSettings();
    final boolean old = editorSettings.isCaretInsideTabs();
    editorSettings.setCaretInsideTabs(false);
    try {
      ApplicationManager.getApplication()
        .runWriteAction(() -> CodeStyleManager.getInstance(getProject()).reformatText(getFile(), 0, getEditor()
          .getDocument().getTextLength()));
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
    checkResultByText(expected);
  }


  public void testAutoWrapRightMargin() {
    int oldValue = mySettings.getDefaultRightMargin();
    boolean oldMarginValue = mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    mySettings.setDefaultRightMargin(80);
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    try {
      configureByFile("/codeInsight/editing/before" + getTestName(false) + ".java");
      for (int i = 0; i != 47; ++i) {
        type(' ');
      }
      checkResultByFile("/codeInsight/editing/after" + getTestName(false) + ".java");
    }
    finally {
      mySettings.setDefaultRightMargin(oldValue);
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldMarginValue;
    }
  }

  public void testAutoWrapLanguageRightMargin() {
    CommonCodeStyleSettings javaCommonSettings = mySettings.getCommonSettings(JavaLanguage.INSTANCE);
    int oldRightMargin = javaCommonSettings.RIGHT_MARGIN;
    boolean oldShouldWrap = mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN;
    javaCommonSettings.RIGHT_MARGIN = 80;
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    try {
      configureByFile("/codeInsight/editing/beforeAutoWrapRightMargin.java");
      for (int i = 0; i != 47; ++i) {
        type(' ');
      }
      checkResultByFile("/codeInsight/editing/afterAutoWrapRightMargin.java");
    }
    finally {
      javaCommonSettings.RIGHT_MARGIN = oldRightMargin;
      mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = oldShouldWrap;
    }
  }

  public void testAutoWrapStringLiteral() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(27);
    doTest(JavaFileType.INSTANCE, 'j');
  }

  public void testJavadocBraces() {
    configureFromFileText("a.java", "/** <caret> */");
    type('{');
    checkResultByText("/** {<caret>} */");
    type('}');
    checkResultByText("/** {}<caret> */");
  }

  public void testSmartIndentOnEnterWithinNonLastStatement() {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.SMART_INDENT_ON_ENTER = true;
      doTest(JavaFileType.INSTANCE, '\n');
      return null;
    });
  }

  public void testEmacsTabWithSelection() {
    configureFromFileText(getTestName(false) + ".java", """
      class Foo {
      <selection>int a;
      int b;<caret></selection>
      }""");
    executeAction(IdeActions.ACTION_EDITOR_EMACS_TAB);
    checkResultByText("""
                        class Foo {
                            <selection>int a;
                            int b;<caret></selection>
                        }""");
  }

  public void testUnindentLineWithTabs() {
    // Inspired by IDEA-76050

    String initial =
      """
        class Test {
        \tvoid test() {
        \t\t<caret>
        \t}
        }""";
    init(initial, JavaFileType.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions =
      getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
    assertNotNull(indentOptions);
    boolean oldUseTabs = indentOptions.USE_TAB_CHARACTER;
    boolean oldUseSmartTabs = indentOptions.SMART_TABS;
    try {
      indentOptions.USE_TAB_CHARACTER = true;
      indentOptions.SMART_TABS = true;

      CodeStyleSettingsManager.getInstance(getProject()).notifyCodeStyleSettingsChanged();

      unindent();
      checkResultByText(
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
  }
}
