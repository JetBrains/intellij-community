// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaEditingTest extends EditingTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
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


  public void testMethodBracesInAnonymous() {
    configureFromFileText("a.java", "class Foo{{ new MethodClosure(new Object() {void fake()<caret>}, 2) }}");
    type('{');
    checkResultByText("class Foo{{ new MethodClosure(new Object() {void fake(){<caret>}}, 2) }}");
    type('}');
    checkResultByText("class Foo{{ new MethodClosure(new Object() {void fake(){}<caret>}, 2) }}");
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
