// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.FixDocCommentAction;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class FixDocCommentTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
    CodeStyle.setTemporarySettings(myFixture.getProject(), CodeStyle.createTestSettings());
    CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    settings.getCommonSettings(JavaLanguage.INSTANCE).DOCUMENTATION_LINE_COMMENT_PREFERRED = false;
    CodeStyle.setTemporarySettings(myFixture.getProject(), settings);
  }

  @AfterEach
  void tearDown() {
    CodeStyle.dropTemporarySettings(myFixture.getProject());
  }

  private <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull Class<T> settingsClass) {
    return CodeStyle.getSettings(myFixture.getProject()).getCustomSettings(settingsClass);
  }

  @Test
  public void testGenerateMethodDoc() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            String test(int i) {
                return "s";<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * @param i <caret>
             * @return
             */
            String test(int i) {
                return "s";
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testGenerateFieldDoc() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            int <caret>i;
        }""";
      String expected = """
        class Test {
            /**
             * <caret>
             */
            int i;
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testGenerateClassDoc() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            void test1() {}
        <caret>
            void test2() {}
        }""";
      String expected = """
        /**
         * <caret>
         */
        class Test {
            void test1() {}

            void test2() {}
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testRemoveOneParameterFromMany() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             * @param i
             * @param j
             * @param k
             */
            void test(int i, int j) {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * @param i <caret>
             * @param j
             */
            void test(int i, int j) {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testRemoveTheOnlyParameter() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             * My description
             * @param i
             */
            void test() {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * My description
             */
            void test() {<caret>
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testRemoveReturn() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             * My description
             * @return data
             */
            void test() {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * My description
             */
            void test() {<caret>
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testRemoveOneThrowsFromMany() {
    myFixture.runTest(() -> {
      String initial = """
        class MyException1 extends Exception {}
        class MyException2 extends Exception {}

        class Test {
            /**
             * @param i  my arg
             * @throws MyException1  text1
             * @throws MyException2  text2
             */
            void test(int i) throws MyException2 {<caret>
            }
        }""";
      String expected = """
        class MyException1 extends Exception {}
        class MyException2 extends Exception {}

        class Test {
            /**
             * @param i  my arg
             * @throws MyException2  text2
             */
            void test(int i) throws MyException2 {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testRemoveTheOnlyThrows() {
    myFixture.runTest(() -> {
      String initial = """
        class MyException extends Exception {}

        class Test {
            /**
             * @param i  my arg
             * @throws MyException  text
             */
            void test(int i) {<caret>
            }
        }""";
      String expected = """
        class MyException extends Exception {}

        class Test {
            /**
             * @param i  my arg
             */
            void test(int i) {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testRemoveOneTypeParameterFromMany() {
    myFixture.runTest(() -> {
      String initial = """
        /**
         * @param <T> tDescription
         * @param <V> vDescription
         */
        class Test<V> {<caret>
        }""";
      String expected = """
        /**
         * @param <V> vDescription
         */
        class Test<V> {<caret>
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testRemoveMultipleTypeParameter() {
    myFixture.runTest(() -> {
      String initial = """
        /**
         * @param <T> tDescription
         * @param <V> vDescription
         */
        class Test {<caret>
        }""";
      String expected = """
        /**
         */
        class Test {<caret>
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAddFirstParameter() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             */\s
            void test(int i) {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * @param i <caret>
             */\s
            void test(int i) {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAddMultipleParameter() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             * @param i
             */\s
            void test(int i, int j, int k) {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * @param i <caret>
             * @param j
             * @param k
             */\s
            void test(int i, int j, int k) {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAddReturn() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             */\s
            int test() {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * @return<caret>
             */\s
            int test() {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAddFirstThrows() {
    myFixture.runTest(() -> {
      String initial = """
        class MyException extends Exception {}

        class Test {
            /**
             * @param i  my arg
             */
            void test(int i) throws MyException {<caret>
            }
        }""";
      String expected = """
        class MyException extends Exception {}

        class Test {
            /**
             * @param i  my arg
             * @throws MyException<caret>
             */
            void test(int i) throws MyException {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAddNonFirstThrows() {
    myFixture.runTest(() -> {
      String initial = """
        class MyException1 extends Exception {}
        class MyException2 extends Exception {}
        class MyException3 extends Exception {}

        class Test {
            /**
             * @param i  my arg
             * @throws MyException1
             */
            void test(int i) throws MyException1, MyException2, MyException3 {<caret>
            }
        }""";
      String expected = """
        class MyException1 extends Exception {}
        class MyException2 extends Exception {}
        class MyException3 extends Exception {}

        class Test {
            /**
             * @param i  my arg
             * @throws MyException1<caret>
             * @throws MyException2
             * @throws MyException3
             */
            void test(int i) throws MyException1, MyException2, MyException3 {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAddFirstThrowsWhenEmptyReturnIsAvailable() {
    myFixture.runTest(() -> {
      String initial = """
        class MyException extends Exception {}

        class Test {
            /**
             * @return
             */
            int test() throws MyException {<caret>
                return 1;
            }
        }""";
      String expected = """
        class MyException extends Exception {}

        class Test {
            /**
             * @return<caret>
             * @throws MyException
             */
            int test() throws MyException {
                return 1;
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAddFirstTypeParameter() {
    myFixture.runTest(() -> {
      String initial = """
        /**
         * My description
         * @author me
         */
        class Test<T> {<caret>
        }""";
      String expected = """
        /**
         * My description
         * @author me
         * @param <T> <caret>
         */
        class Test<T> {
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAddNonFirstTypeParameter() {
    myFixture.runTest(() -> {
      String initial = """
        /**
         * My description
         * @author me
         * @param <T>    type description<caret>
         */
        class Test<T, V> {
        }""";
      String expected = """
        /**
         * My description
         * @author me
         * @param <T>    type description
         * @param <V> <caret>
         */
        class Test<T, V> {
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testCorrectParametersOrder() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             * @param j
             * @param k    single line description
             * @param i    multi-line
             *             description
             */
            public void test(int i, int j, int k) {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * @param i    multi-line
             *             description
             * @param j    <caret>
             * @param k    single line description
             */
            public void test(int i, int j, int k) {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testCorrectParametersDescriptionWhenIndentIsDefines() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             * @param j   \s
             * @param i
             */
            public void test(int i, int j) {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * @param i <caret>
             * @param j   \s
             */
            public void test(int i, int j) {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testCorrectMethodTypeParametersOrder() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
          /**
           * @param <B>
           * @param <A>    A description
           */
          <A, B> void test() {<caret>
          }
        }""";
      String expected = """
        class Test {
          /**
           * @param <A>    A description
           * @param <B> <caret>
           */
          <A, B> void test() {
          }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testCorrectClassTypeParametersOrder() {
    myFixture.runTest(() -> {
      String initial = """
        /**
         * Class description
         * @author Zigmund
         * @param <B>    multi-line
         *               description
         * @param <A>
         */
        class Test<A, B> {<caret>
        }""";
      String expected = """
        /**
         * Class description
         * @author Zigmund
         * @param <A> <caret>
         * @param <B>    multi-line
         *               description
         */
        class Test<A, B> {
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testAllesZusammen() {
    myFixture.runTest(() -> {
      String initial = """
        class MyException1 extends Exception {}
        class MyException2 extends Exception {}

        class Test {
            /**
             * Method description
             * @param j    j description (single line)
             * @param s    s description
             * @param k
             *             k description (single line but located at another line)
             * @throws MyException2
             * @return some value
             */
            void test(int i, int j, int k) throws MyException1 {<caret>
            }
        }""";
      String expected = """
        class MyException1 extends Exception {}
        class MyException2 extends Exception {}

        class Test {
            /**
             * Method description
             * @param i    <caret>
             * @param j    j description (single line)
             * @param k
             *             k description (single line but located at another line)
             * @throws MyException1
             */
            void test(int i, int j, int k) throws MyException1 {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testNavigateToMissingParamDescription() {
    myFixture.runTest(() -> {
      String initial = """
        class Test {
            /**
             * @param i
             */\s
            void test(int i) {<caret>
            }
        }""";
      String expected = """
        class Test {
            /**
             * @param i <caret>
             */\s
            void test(int i) {
            }
        }""";
      doTest(initial, expected);
    });
  }

  @Test
  public void test_many_newlines_before_interface() {
    myFixture.runTest(() -> {
      String initial = """

        interface <caret>I {}""";
      String expected = """
        /**
         *\s
         */
        interface I {}""";
      doTest(initial, expected);
    });
  }

  @Test
  public void testWithEmptyTagsRemovalOption() {
    myFixture.runTest(() -> {
      JavaCodeStyleSettings settings = getCustomSettings(JavaCodeStyleSettings.class);
      settings.JD_KEEP_EMPTY_PARAMETER = false;
      settings.JD_KEEP_EMPTY_RETURN = false;
      settings.JD_KEEP_EMPTY_EXCEPTION = false;
      String initial =  """
        package com.company;

        public class Test
        {
            int foo<caret>(String s, int i, double d) throws Exception
            {
                return 0;
            }
        }
        """;
      String expected = """
        package com.company;

        public class Test
        {
            /**
             * @param s <caret>
             * @param i
             * @param d
             * @return
             * @throws Exception
             */
            int foo(String s, int i, double d) throws Exception
            {
                return 0;
            }
        }
        """;
      doTest(initial, expected);
    });
  }

  private void doTest(String initial, String expected) {
    myFixture.configureFromFileText(myFixture.getTestName() + ".java", initial);
    myFixture.getEditor().getSettings().setVirtualSpace(false);
    myFixture.executeAction(FixDocCommentAction.ACTION_ID);
    myFixture.checkResultByText(expected);
  }
}
