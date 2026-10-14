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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractBasicJavaEditingTest extends EditingTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }


  @Test
  public void testAutoWrapAndTrailingWhiteSpaces() {
    myFixture.runTest(() -> {
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
      myFixture.backspace();
      int offset = myFixture.getEditor().getDocument().getText().indexOf("comment");
      myFixture.getEditor().getCaretModel().moveToOffset(offset + "comment".length());
      for (int i = 0; i < 20; i++) {
        myFixture.type(' ');
      }

      // Check that no unnecessary line feed is introduced.
      assertEquals(4, myFixture.getEditor().getDocument().getLineCount());
    });
  }


  @Test
  public void testMethodBracesInAnonymous() {
    myFixture.runTest(() -> {
      myFixture.configureFromFileText("a.java", "class Foo{{ new MethodClosure(new Object() {void fake()<caret>}, 2) }}");
      myFixture.type('{');
      myFixture.checkResultByText("class Foo{{ new MethodClosure(new Object() {void fake(){<caret>}}, 2) }}");
      myFixture.type('}');
      myFixture.checkResultByText("class Foo{{ new MethodClosure(new Object() {void fake(){}<caret>}, 2) }}");
    });
  }

  @Test
  public void testNoClosingTagInsertedForJavaDocTypeParameter() {
    myFixture.runTest(() -> {
      // Inspired by IDEA-70898.

      String initial =
        """
          /**
           * @param <T<caret>
           */
          class Test<T> {
          }""";
      myFixture.init(initial, JavaFileType.INSTANCE);
      final CodeInsightSettings settings = CodeInsightSettings.getInstance();
      final boolean old = settings.JAVADOC_GENERATE_CLOSING_TAG;

      try {
        settings.JAVADOC_GENERATE_CLOSING_TAG = true;
        myFixture.type("> <test-tag>");
        String expected =
          """
            /**
             * @param <T> <test-tag><caret></test-tag>
             */
            class Test<T> {
            }""";
        myFixture.checkResultByText(expected);
      }
      finally {
        settings.JAVADOC_GENERATE_CLOSING_TAG = old;
      }
    });
  }

  @Test
  public void testHungryBackspaceWinsOverSmartBackspace() {
    myFixture.runTest(() -> {
      myFixture.init("""
             class Test {
                 void m() {
                \s
                 <caret>}
             }""", JavaFileType.INSTANCE);
      myFixture.executeAction("EditorHungryBackSpace");
      myFixture.checkResultByText("""
                          class Test {
                              void m() {<caret>}
                          }""");
    });
  }

  @Test
  public void testDeleteToWordStartAndQuotes() {
    myFixture.runTest(() -> {
      String text = "one \"two\" <caret>\"three\"";
      myFixture.init(text, JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
      myFixture.checkResultByText("one <caret>\"three\"");
    });
  }

  @Test
  public void testDeleteToWordStartAndSingleQuotes() {
    myFixture.runTest(() -> {
      String text = "one 'two' <caret>'three'";
      myFixture.init(text, JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
      myFixture.checkResultByText("one <caret>'three'");
    });
  }

  @Test
  public void testDeleteToWordStartWhenCaretInsideQuotes() {
    myFixture.runTest(() -> {
      String text = "one \"two<caret>\"";
      myFixture.init(text, JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
      myFixture.checkResultByText("one \"<caret>\"");
    });
  }

  @Test
  public void testDeleteToWordStartWhenCaretAfterCommaSeparatedLiterals() {
    myFixture.runTest(() -> {
      String text = "one \"two\",  <caret>";
      myFixture.init(text, JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
      myFixture.checkResultByText("one \"two\"<caret>");
    });
  }

  @Test
  public void testDeleteToWordStartWhenCaretBetweenTwoLiterals() {
    myFixture.runTest(() -> {
      String text = "one \"two\"<caret>\"three\"";
      myFixture.init(text, JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
      myFixture.checkResultByText("one <caret>\"three\"");
    });
  }

  @Test
  public void testNoTypeParameterClosingTagCompletion() {
    myFixture.runTest(() -> {
      myFixture.init(
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
        myFixture.type('>');
      }
      finally {
        settings.JAVADOC_GENERATE_CLOSING_TAG = oldValue;
      }
      myFixture.checkResultByText(
        """
          /**
           * @param <P>
           * @author <a href='mailto:xxx@xxx.com'>Mr. Smith</a>
           */
          public class Test<P> {
          }"""
      );
    });
  }

  @Test
  public void testSmartHomeInJavadoc() {
    myFixture.runTest(() -> {
      myFixture.init("""
             /**
              * some text<caret>
              */
             class C {}""",
           JavaFileType.INSTANCE);
      myFixture.home();
      myFixture.checkResultByText("""
                          /**
                           * <caret>some text
                           */
                          class C {}""");
    });
  }

  @Test
  public void testSmartHomeWithSelectionInJavadoc() {
    myFixture.runTest(() -> {
      myFixture.init("""
             /**
              * some text<caret>
              */
             class C {}""",
           JavaFileType.INSTANCE);
      myFixture.homeWithSelection();
      myFixture.checkResultByText("""
                          /**
                           * <selection><caret>some text</selection>
                           */
                          class C {}""");
    });
  }
}
