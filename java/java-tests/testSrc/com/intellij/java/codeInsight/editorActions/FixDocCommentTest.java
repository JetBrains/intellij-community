// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.FixDocCommentAction;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

public class FixDocCommentTest extends AbstractEditorTest {
  public void testGenerateMethodDoc() {
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
  }

  public void testGenerateFieldDoc() {
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
  }

  public void testGenerateClassDoc() {
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
  }

  public void testRemoveOneParameterFromMany() {
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
  }

  public void testRemoveTheOnlyParameter() {
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
  }

  public void testRemoveReturn() {
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
  }

  public void testRemoveOneThrowsFromMany() {
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
  }

  public void testRemoveTheOnlyThrows() {
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
  }

  public void testRemoveOneTypeParameterFromMany() {
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
  }

  public void testRemoveMultipleTypeParameter() {
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
  }

  public void testAddFirstParameter() {
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
  }

  public void testAddMultipleParameter() {
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
  }

  public void testAddReturn() {
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
  }

  public void testAddFirstThrows() {
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
  }

  public void testAddNonFirstThrows() {
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
  }

  public void testAddFirstThrowsWhenEmptyReturnIsAvailable() {
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
  }

  public void testAddFirstTypeParameter() {
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
  }

  public void testAddNonFirstTypeParameter() {
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
  }

  public void testCorrectParametersOrder() {
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
  }

  public void testCorrectParametersDescriptionWhenIndentIsDefines() {
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
  }

  public void testCorrectMethodTypeParametersOrder() {
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
  }

  public void testCorrectClassTypeParametersOrder() {
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
  }

  public void testAllesZusammen() {
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
  }

  public void testNavigateToMissingParamDescription() {
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
  }

  public void test_many_newlines_before_interface() {
    String initial = """




      interface <caret>I {}""";
    String expected = """
      /**
       *\s
       */
      interface I {}""";
    doTest(initial, expected);
  }

  public void testWithEmptyTagsRemovalOption() {
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
  }

  private void doTest(String initial, String expected) {
    configureFromFileText(getTestName(false) + ".java", initial);
    getEditor().getSettings().setVirtualSpace(false);
    executeAction(FixDocCommentAction.ACTION_ID);
    checkResultByText(expected);
  }
}
