/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.formatter.java;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Is intended to test formatting in editor behavior, i.e. check how formatting affects things like caret position, selection etc.
 */
@TestApplication
public class JavaFormatterInEditorTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testCaretPositionOnLongLineWrapping() {
    // Inspired by IDEA-70242
    CommonCodeStyleSettings javaCommonSettings = CodeStyle.getSettings(codeInsightFixture.get().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    javaCommonSettings.WRAP_LONG_LINES = true;
    javaCommonSettings.RIGHT_MARGIN = 40;
    doTest(
      """
        import static java.util.concurrent.atomic.AtomicInteger.*;

        /**
         * Some really long javadoc comment which exceeeds the right margin
         */
        class <caret>Test {
        }""",

      """
        import static java.util.concurrent.atomic.AtomicInteger.*;

        /**
         * Some really long javadoc comment\s
         * which exceeeds the right margin
         */
        class <caret>Test {
        }"""
    );
  }

  @Test
  public void testCaretPositionPreserved_WhenOnSameLineWithWhiteSpacesOnly() {
    String text = """
      class Test {
          void test() {
               <caret>
          }
      }""";
    doTest(text, text);

    String before = """
      class Test {
          void test() {
               <caret>      \s
          }
      }""";
    doTest(before, text);
  }

  @Test
  public void testCaretPositionPreserved_WhenSomeFormattingNeeded() {
    String before = """
      public class Test {
              int a;
         \s
          public static void main(String[] args) {
                           <caret>
          }

          static final long j = 2;
      }""";
    String after = """
      public class Test {
          int a;

          public static void main(String[] args) {
                           <caret>
          }

          static final long j = 2;
      }""";
    doTest(before, after);

    before = """
      public class Test {
              int a;
         \s
          public static void main(String[] args) {
                           <caret>          \s
          }

          static final long j = 2;
      }""";
    doTest(before, after);
  }

  @Test
  public void testCaretLineAndPositionPreserved_WhenBracketOnNextLineWillBeFormatted() {
    String before = """
      public class Test {
              int a;
         \s
          public static void main(String[] args) {
                           <caret>
                  }

          static final long j = 2;
      }""";
    String after = """
      public class Test {
          int a;

          public static void main(String[] args) {
                           <caret>
          }

          static final long j = 2;
      }""";
    doTest(before, after);

    before = """
      public class Test {
              int a;
         \s
          public static void main(String[] args) {
                           <caret>          \s
                      }

          static final long j = 2;
      }""";
    doTest(before, after);
  }

  @Test
  public void testKeepIndentsOnBlankLinesCaretPosition() {
    CommonCodeStyleSettings.IndentOptions indentOptions =
      CodeStyle.getSettings(codeInsightFixture.get().getProject()).getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
    assertNotNull(indentOptions);
    indentOptions.KEEP_INDENTS_ON_EMPTY_LINES = true;
    final String initial =
      """
        public class Main {
            public void foo(boolean a, int x, int y, int z) {
                do {
                    if (x > 0) {
                        <caret>
                    }
                }
                while (y > 0);
            }
        }""";

    doTest(
      initial,

      """
        public class Main {
            public void foo(boolean a, int x, int y, int z) {
                do {
                    if (x > 0) {
                        <caret>
                    }
                }
                while (y > 0);
            }
        }"""
    );
  }
  
  @Test
  public void testKeepWhitespacesOnEmptyLines() {
    CommonCodeStyleSettings.IndentOptions indentOptions =
      CodeStyle.getSettings(codeInsightFixture.get().getProject()).getCommonSettings(JavaLanguage.INSTANCE).getIndentOptions();
    assertNotNull(indentOptions);
    boolean keepIndents = indentOptions.KEEP_INDENTS_ON_EMPTY_LINES;
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    String stripSpaces = editorSettings.getStripTrailingSpaces();
    try {
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
      indentOptions.KEEP_INDENTS_ON_EMPTY_LINES = true;
      final String initial =
        """
          <caret>package com.acme;

          class Foo {
              Integer[] foo() {
                   \s
                      \s
                  return new Integer[]{0, 1};
              }
               \s
          }""";
      
      final String expected =
        """
           package com.acme;

          class Foo {
              Integer[] foo() {
                 \s
                 \s
                  return new Integer[]{0, 1};
              }
             \s
          }""";

      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.configureByText(myFixture.getTestName(false) + ".java", initial);
      //WriteCommandAction.runWriteCommandAction(getProject(), () -> CodeStyleManager.getInstance(getProject())
      //  .reformatText(getFile(), 0, getEditor().getDocument().getTextLength()));
      myFixture.runInCommand(() -> {
        Document doc = myFixture.getEditor().getDocument();
        EditorTestUtil.performTypingAction(myFixture.getEditor(), ' ');
        PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(doc);
        FileDocumentManager.getInstance().saveAllDocuments();
        myFixture.checkResult(expected);
      });
    }
    finally {
      indentOptions.KEEP_INDENTS_ON_EMPTY_LINES = keepIndents;
      editorSettings.setStripTrailingSpaces(stripSpaces);
    }
  } 

  private void doTest(@NotNull String before, @NotNull String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(myFixture.getTestName(false) + ".java", before);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> CodeStyleManager.getInstance(myFixture.getProject()).reformatText(myFixture.getFile(), 0, myFixture.getEditor().getDocument().getTextLength()));

    myFixture.checkResult(after);
  }
}