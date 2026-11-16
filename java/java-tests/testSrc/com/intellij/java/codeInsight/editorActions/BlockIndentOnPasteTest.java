// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypingActionsExtension;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class BlockIndentOnPasteTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testJavaBlockDecreasedIndentOnTwoLinesPasting() {
    String before = """
      class Test {
          void test() {
              if (true) {
                  <caret>
              }
          }
      }""";

    String toPaste = """
      foo();
         foo();""";

    String expected = """
      class Test {
          void test() {
              if (true) {
                  foo();
                     foo();
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testStringBeforeAnotherStringShouldNotIncreaseIndentOfTheFollowingString() {
    String before = """
      class Test {
          void test() {
      <caret>        int a = 100;
              int b = 200;
          }""";

    String toPaste = """
          int b = 200;
      """;

    String expected = """
      class Test {
          void test() {
              int b = 200;
              int a = 100;
              int b = 200;
          }""";

    doTest(before, toPaste, expected);
  }

  @Test
  public void testJavaComplexBlockWithDecreasedIndent() {
    String before = """
      class Test {
          void test() {
              if (true) {
                  i = 1;
              } else {
                  i = 2;
              }
                                    <caret>
          }
      }""";

    String toPaste = """
      if (true) {
          i = 1;
      } else {
          i = 2;
      }""";

    String expected = """
      class Test {
          void test() {
              if (true) {
                  i = 1;
              } else {
                  i = 2;
              }
              if (true) {
                  i = 1;
              } else {
                  i = 2;
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testJavaBlockIncreasedIndentOnTwoLinesPasting() {
    String before = """
      class Test {
          void test() {
              if (true) {
                  <caret>
              }
          }
      }""";

    String toPaste = """
      foo();
         foo();""";

    String expected = """
      class Test {
          void test() {
              if (true) {
                  foo();
                     foo();
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testPastingBlockEndedByLineFeed() {
    String before = """
      class Test {
          void test() {
              if (true) {
              <caret>}
          }
      }""";

    String toPaste = """
      int i = 1;
      """;

    String expected = """
      class Test {
          void test() {
              if (true) {
                  int i = 1;
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testPasteAtZeroColumn() {
    String before = """
      class Test {
          void test() {
           if (true) {
      <caret>
           }
          }
      }""";

    String toPaste = """
      foo();
       foo();""";

    String expected = """
      class Test {
          void test() {
           if (true) {
               foo();
                foo();
           }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testPasteAtDocumentStartColumn() {
    String before = "<caret>";

    String toPaste = """
      class Test {
      }""";

    String expected = """
      class Test {
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testBlockDecreasedIndentOnThreeLinesPasting() {
    String before = """
      class Test {
          void test() {
              if (true) {
                  <caret>
              }
          }
      }""";

    String toPaste = """
      foo();
      foo();
         foo();""";

    String expected = """
      class Test {
          void test() {
              if (true) {
                  foo();
                  foo();
                     foo();
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testBlockIncreasedIndentOnThreeLinesPasting() {
    String before = """
      class Test {
          void test() {
              if (true) {
                  <caret>
              }
          }
      }""";

    String toPaste = """
      foo();
       foo();
          foo();""";

    String expected = """
      class Test {
          void test() {
              if (true) {
                  foo();
                   foo();
                      foo();
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testBlockWithIndentOnFirstLine() {
    String before = """
      class Test {
          void test() {
              if (true) {
                  <caret>
              }
          }
      }""";

    String toPaste = """
      foo();
         foo();
       foo();""";

    String expected = """
      class Test {
          void test() {
              if (true) {
                  foo();
                     foo();
                   foo();
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testPasteAfterExistingSymbols() {
    String before = """
      class Test {
          void test() {
              if (true) {<caret>
              }
          }
      }""";

    String toPaste = """
      // this is a comment
       foo();
        foo();
      foo();""";

    String expected = """
      class Test {
          void test() {
              if (true) {// this is a comment
               foo();
                foo();
              foo();
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testPasteAtZeroColumnAfterBlankLineWithWhiteSpaces() {
    String before = """
      class Test {
          void test() {
              if (true) {
              }
          }
      }
        \s
      <caret>""";

    String toPaste = """
      class Test {
          void test() {
              if (true) {
              }
          }
      }""";

    String expected = """
      class Test {
          void test() {
              if (true) {
              }
          }
      }

      class Test {
          void test() {
              if (true) {
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testPasteAtNonZeroColumnAfterBlankLineWithWhiteSpaces() {
    String before = """
      class Test {
          void test() {
              if (true) {
              }
          }
      }
        \s
          <caret>""";

    String toPaste = """
      class Test {
          void test() {
              if (true) {
              }
          }
      }""";

    String expected = """
      class Test {
          void test() {
              if (true) {
              }
          }
      }
        \s
      class Test {
          void test() {
              if (true) {
              }
          }
      }""";
    doTest(before, toPaste, expected);
  }

  @Test
  public void testPasteToNonEmptyStringTextWithTrailingLineFeed() {
    String before = """
      class Test {
          void test() {
              foo(1, <caret>);
          }
      }""";

    String toPaste1 = """
      calculate(3, 4)
      """;

    String expected = """
      class Test {
          void test() {
              foo(1, calculate(3, 4)
              );
          }
      }""";
    doTest(before, toPaste1, expected);

    String toPaste2 = """
      calculate(3, 4)
         \s""";
    doTest(before, toPaste2, expected);
  }

  @Test
  public void testPasteTextThatStartsWithLineFeedAfterNonEmptyLine() {
    String before = """
      class Test {
          void test() {
              foo(1);
          }<caret>
      }""";

    String toPaste1 = """

          void test() {
              foo(1);
          }\
      """;

    String expected1 = """
      class Test {
          void test() {
              foo(1);
          }
          void test() {
              foo(1);
          }
      }""";
    doTest(before, toPaste1, expected1);

    String toPaste2 = """



          void test() {
              foo(1);
          }\
      """;

    String expected2 = """
      class Test {
          void test() {
              foo(1);
          }


          void test() {
              foo(1);
          }
      }""";
    doTest(before, toPaste2, expected2);
  }

  @Test
  public void testPasteTextThatStartsWithLineFeedToNewLine() {
    String before = """
      class Test {
          void test(int i) {
              if (i > 0) {<caret>
              }
          }
      }""";

    String toPaste1 = """

      if (i > 2) {
      }""";

    String expected1 = """
      class Test {
          void test(int i) {
              if (i > 0) {
                  if (i > 2) {
                  }
              }
          }
      }""";
    doTest(before, toPaste1, expected1);

    String toPaste2 = """



          if (i > 2) {
          }\
      """;

    String expected2 = """
      class Test {
          void test(int i) {
              if (i > 0) {


                  if (i > 2) {
                  }
              }
          }
      }""";
    doTest(before, toPaste2, expected2);
  }

  @Test
  public void testPasteMultilineTextThatStartsWithLineFeedToNewLineWithBigCaretIndent() {
    String before = """
      class Test {
          void test(int i) {
              if (i > 0) {
                       <caret>
              }
          }
      }""";

    String toPaste = """

                  test(1);
                  test(1);\
      """;

    String expected = """
      class Test {
          void test(int i) {
              if (i > 0) {
                      \s
                  test(1);
                  test(1);
              }
          }
      }""";
    doTest(before, toPaste, expected);

    String toPaste2 = """

           test(1);
           test(1);\
      """;
    doTest(before, toPaste2, expected);
  }

  @Test
  public void testPlainTextPaste() {
    String before = """
      line1
      line2
         <caret>""";

    String toPaste = """
      line to paste #1
           line to paste #2""";

    String expected = """
      line1
      line2
         line to paste #1
              line to paste #2""";
    doTest(before, toPaste, expected, FileTypes.PLAIN_TEXT);
  }

  @Test
  public void testPlainTextWhenPastedStringEndsByLineFeed() {
    String before = """
        line1
        line2
        <caret>
      """;

    String toPaste = """
      line to paste #1
      line to paste #2
      """;

    String expected = """
        line1
        line2
        line to paste #1
        line to paste #2

      """;
    doTest(before, toPaste, expected, FileTypes.PLAIN_TEXT);
  }

  @Test
  public void testPlainTextWhenCaretIsAfterSelection() {
    String before = """
      <selection>  line1
      </selection><caret>""";

    String toPaste = """
      line to paste #1
      line to paste #2
      """;

    String expected = """
        line1
      line to paste #1
      line to paste #2
      """;
    doTest(before, toPaste, expected, FileTypes.PLAIN_TEXT);
  }

  @Test
  public void testPlainTextThatStartsByLineFeed() {
    String before = """
      line 1
        # item1<caret>
      """;

    String toPaste1 = """

        # item2\
      """;

    String expected1 = """
      line 1
        # item1
        # item2
      """;
    doTest(before, toPaste1, expected1, FileTypes.PLAIN_TEXT);

    String toPaste2 = """



        # item2\
      """;

    String expected2 = """
      line 1
        # item1


        # item2
      """;
    doTest(before, toPaste2, expected2, FileTypes.PLAIN_TEXT);
  }

  @Test
  public void testFormatterBasedPasteThatStartsWithWhiteSpace() {
    String before = """
      class Test {
          int i;
          int j;

          void test() {
              <caret>
          }
      }
      """;

    String toPaste = """
      int i;
      int j;""";

    String expected = """
      class Test {
          int i;
          int j;

          void test() {
              int i;
              int j;
          }
      }
      """;
    doTest(before, toPaste, expected);
  }

  public void doTest(String before, String toPaste, String expected, FileType fileType) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(fileType, before);

    EdtTestUtil.runInEdtAndWait(() -> {
      CodeInsightSettings settings = CodeInsightSettings.getInstance();
      int old = settings.REFORMAT_ON_PASTE;
      settings.REFORMAT_ON_PASTE = CodeInsightSettings.INDENT_BLOCK;
      try {
        int offset = myFixture.getEditor().getCaretModel().getOffset();
        int column = myFixture.getEditor().getCaretModel().getLogicalPosition().column;
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
          myFixture.getEditor().getDocument().insertString(offset, toPaste);
          TypingActionsExtension.findForContext(myFixture.getProject(), myFixture.getEditor())
            .format(myFixture.getProject(), myFixture.getEditor(), CodeInsightSettings.INDENT_BLOCK, offset, offset + toPaste.length(), column, false,
                    false);
          }
        );
      }
      finally {
        settings.REFORMAT_ON_PASTE = old;
      }

      myFixture.getEditor().getSelectionModel().removeSelection();
    });
    myFixture.checkResult(expected);
  }

  public void doTest(String before, String toPaste, String expected) {
    doTest(before, toPaste, expected, JavaFileType.INSTANCE);
  }
}
