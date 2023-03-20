// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class JavaEditingTest extends EditingTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testAutoWrapStringLiteral() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(27);
    doTest(JavaFileType.INSTANCE, 'j');
  }

  public void testAutoWrapAndTrailingWhiteSpaces() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(30);
    CommonCodeStyleSettings.IndentOptions indentOptions = mySettings.getIndentOptions(null);
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.TAB_SIZE = 4;
    indentOptions.CONTINUATION_INDENT_SIZE = 4;
    UISettings.getInstance().setFontFace("Tahoma");
    UISettings.getInstance().setFontSize(11);

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    scheme.setEditorFontName("Tahoma");
    scheme.setEditorFontSize(11);

    configureByFile("/codeInsight/editing/before" + getTestName(false) + ".java");
    backspace();
    int offset = getEditor().getDocument().getText().indexOf("comment");
    getEditor().getCaretModel().moveToOffset(offset + "comment".length());
    for (int i = 0; i < 20; i++) {
      type(' ');
    }

    // Check that no unnecessary line feed is introduced.
    assertEquals(4, getEditor().getDocument().getLineCount());
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

  public void testAutoWrapJavadoc() {
    mySettings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true;
    mySettings.setDefaultRightMargin(31);
    doTest(JavaFileType.INSTANCE, 'c');
  }

  public void testSmartIndentOnEnterWithinNonLastStatement() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean oldValue = settings.SMART_INDENT_ON_ENTER;
    try {
      doTest(JavaFileType.INSTANCE, '\n');
    }
    finally {
      settings.SMART_INDENT_ON_ENTER = oldValue;
    }
  }

  public void testJavadocBraces() {
    configureFromFileText("a.java", "/** <caret> */");
    type('{');
    checkResultByText("/** {<caret>} */");
    type('}');
    checkResultByText("/** {}<caret> */");
  }

  public void testMethodBracesInAnonymous() {
    configureFromFileText("a.java", "class Foo{{ new MethodClosure(new Object() {void fake()<caret>}, 2) }}");
    type('{');
    checkResultByText("class Foo{{ new MethodClosure(new Object() {void fake(){<caret>}}, 2) }}");
    type('}');
    checkResultByText("class Foo{{ new MethodClosure(new Object() {void fake(){}<caret>}, 2) }}");
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
      ApplicationManager.getApplication().runWriteAction(() -> CodeStyleManager.getInstance(getProject()).reformatText(getFile(), 0, getEditor()
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

  public void testNoClosingTagInsertedForJavaDocTypeParameter() {
    // Inspired by IDEA-70898.

    String initial =
      """
        /**
         * @param <T<caret>
         */
        class Test<T> {
        }""";
    init(initial, JavaFileType.INSTANCE);
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    final boolean old = settings.JAVADOC_GENERATE_CLOSING_TAG;

    try {
      settings.JAVADOC_GENERATE_CLOSING_TAG = true;
      type("> <test-tag>");
      String expected =
        """
          /**
           * @param <T> <test-tag><caret></test-tag>
           */
          class Test<T> {
          }""";
      checkResultByText(expected);
    }
    finally {
      settings.JAVADOC_GENERATE_CLOSING_TAG = old;
    }
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
    indentOptions.USE_TAB_CHARACTER = true;
    boolean oldUseSmartTabs = indentOptions.SMART_TABS;
    indentOptions.SMART_TABS = true;
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

  public void testHungryBackspaceWinsOverSmartBackspace() {
    init("""
           class Test {
               void m() {
              \s
               <caret>}
           }""", JavaFileType.INSTANCE);
    executeAction("EditorHungryBackSpace");
    checkResultByText("""
                        class Test {
                            void m() {<caret>}
                        }""");
  }

  public void testDeleteToWordStartAndQuotes() {
    String text = "one \"two\" <caret>\"three\"";
    init(text, JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("one <caret>\"three\"");
  }

  public void testDeleteToWordStartAndSingleQuotes() {
    String text = "one 'two' <caret>'three'";
    init(text, JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("one <caret>'three'");
  }

  public void testDeleteToWordStartWhenCaretInsideQuotes() {
    String text = "one \"two<caret>\"";
    init(text, JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("one \"<caret>\"");
  }

  public void testDeleteToWordStartWhenCaretAfterCommaSeparatedLiterals() {
    String text = "one \"two\",  <caret>";
    init(text, JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("one \"two\"<caret>");
  }

  public void testDeleteToWordStartWhenCaretBetweenTwoLiterals() {
    String text = "one \"two\"<caret>\"three\"";
    init(text, JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("one <caret>\"three\"");
  }

  public void testNoTypeParameterClosingTagCompletion() {
    init(
      """
        /**
         * @param <P<caret>
         * @author <a href='mailto:xxx@xxx.com'>Mr. Smith</a>
         */
        public class Test<P> {
        }""",
      JavaFileType.INSTANCE
    );

    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean oldValue = settings.JAVADOC_GENERATE_CLOSING_TAG;
    settings.JAVADOC_GENERATE_CLOSING_TAG = true;
    try {
      type('>');
    }
    finally {
      settings.JAVADOC_GENERATE_CLOSING_TAG = oldValue;
    }
    checkResultByText(
      """
        /**
         * @param <P>
         * @author <a href='mailto:xxx@xxx.com'>Mr. Smith</a>
         */
        public class Test<P> {
        }"""
    );
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

  public void testSmartHomeInJavadoc() {
    init("""
           /**
            * some text<caret>
            */
           class C {}""",
         JavaFileType.INSTANCE);
    home();
    checkResultByText("""
                        /**
                         * <caret>some text
                         */
                        class C {}""");
  }

  public void testSmartHomeWithSelectionInJavadoc() {
    init("""
           /**
            * some text<caret>
            */
           class C {}""",
         JavaFileType.INSTANCE);
    homeWithSelection();
    checkResultByText("""
                        /**
                         * <selection><caret>some text</selection>
                         */
                        class C {}""");
  }
}
