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

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;

/**
 * Is intended to hold specific java formatting tests for 'braces placement' settings (
 * {@code Project Settings - Code Style - Alignment and Braces}).
 */
@TestApplication
public class JavaFormatterBracesTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);

  @Test
  public void testBracePositioningAtPreviousLine() {
    // Inspired by IDEADEV-18529
    formatterFixture.get().doTextTest(
      """
        public class TestBed
        {
            public void methodOne()
            {
                //code...
            }

            @SomeAnnotation
                    <T extends Comparable> void methodTwo(T item) {
                //code...
            }

            private void methodThree(String s) {
                //code...
            }
        }""",

      """
        public class TestBed {
            public void methodOne() {
                //code...
            }

            @SomeAnnotation
            <T extends Comparable> void methodTwo(T item) {
                //code...
            }

            private void methodThree(String s) {
                //code...
            }
        }""");
  }

  @Test
  public void testSimpleBlockInOneLinesAndForceBraces() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-19328
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;

    formatter.doMethodTest(
      "if (x > y) System.out.println(\"foo!\");",

      "if (x > y) { System.out.println(\"foo!\"); }"
    );
  }

  @Test
  public void testEnforcingBracesForExpressionEndingWithLineComment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-57936
    formatter.getSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    formatter.doMethodTest(
      "if (true) i = 1; // Cool if\n" +
      "else i = 2;",

      """
        if (true) {
            i = 1; // Cool if
        } else {
            i = 2;
        }"""
    );
  }

  @Test
  public void testMoveBraceOnNextLineForAnnotatedMethod() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-59336
    formatter.getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;

    formatter.doClassTest(
      """
        @Override
        public int hashCode() {
        }
        @Deprecated
        void foo() {
        }""",
      """
        @Override
        public int hashCode()
        {
        }

        @Deprecated
        void foo()
        {
        }"""
    );
  }
  
  @Test
  public void testKeepSimpleClassesAndInterfacesInOneLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-65433
    formatter.getSettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    
    String[] tests = {
      "class Test {}",
      
      "interface Test {}",

      """
class Test {
    void test() {
        new Object() {};
    }
}""",

      """
class Test {
    void test() {
        bind(new TypeLiteral<MyType>() {}).toProvider(MyProvider.class);
    }
}"""
    };

    for (String test : tests) {
      formatter.doTextTest(test, test);
    }
  }

  @Test
  public void testKeepSimpleClassesInOneLineAndLeftBraceOnNextLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    // Inspired by IDEA-75053.
    formatter.getSettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    formatter.getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    
    String text =
      """
        class Test
        {
            void foo() {
                bind(new TypeLiteral<MyType>() {}).toProvider(MyProvider.class);
            }
        }""";
    formatter.doTextTest(text, text);
  }

  @Test
  public void testSimpleMethodsInOneLineEvenIfExceedsRightMargin() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.getSettings().RIGHT_MARGIN = 90;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    String text = """
      public class Repr2 {
          public void start() { System.out.println("kfjsdkfjsdkfjskdjfslkdjfklsdjfklsdjfksjdfkljsdkfjsd!"); }
      }""";
    formatter.doTextTest(text, text);
  }

  @Test
  public void testKeepSimpleBlocksInOneLine_OnIfStatementsThenBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    String singleLine = "if (2 > 3) { System.out.println(\"AA!\"); }";

    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    formatter.doMethodTest(singleLine, singleLine);

    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    formatter.doMethodTest(singleLine, singleLine);
  }

  @Test
  public void testKeepSimpleBlocksInOneLine_OnIfStatementsElseBlock() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;

    String before = """
      if (2 > 3) {
          System.out.println("AA!");
      } else { int a = 3; }""";

    String afterNextLineOption = """
      if (2 > 3)
      {
          System.out.println("AA!");
      } else { int a = 3; }""";

    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    formatter.doMethodTest(before, afterNextLineOption);

    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    formatter.doMethodTest(before, before);
  }

  @Test
  public void testIfStatement_WhenBraceOnNextLine_AndKeepSimpleBlockInOneLineEnabled() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    String before = """
      if (2 > 3) {
          System.out.println("AA!");
      }""";
    String after = """
      if (2 > 3)
      {
          System.out.println("AA!");
      }""";
    formatter.doMethodTest(before, after);
  }

  @Test
  public void testIfStatementElseBranchIsOnNewLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    String before = """
      if (2 > 3) {
          System.out.println("AA!");
      } else {
          int a = 3;
      }""";
    String after = """
      if (2 > 3)
      {
          System.out.println("AA!");
      } else
      {
          int a = 3;
      }""";
    formatter.doMethodTest(before, after);
  }

  @Test
  public void testIfElseBranchesKeepedInOneLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().SPACE_WITHIN_BRACES = true;
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    String singleLine = "if (2 > 3) { System.out.println(\"AA!\"); } else { System.out.println(\"BBB!!\"); }";
    String multiLine = "if (2 > 3) { System.out.println(\"AA!\"); }\n" +
                       "else { System.out.println(\"BBB!!\"); }";

    formatter.getSettings().ELSE_ON_NEW_LINE = false;
    formatter.doMethodTest(singleLine, singleLine);
    formatter.doMethodTest(multiLine, singleLine);

    formatter.getSettings().ELSE_ON_NEW_LINE = true;
    formatter.doMethodTest(singleLine, multiLine);
    formatter.doMethodTest(multiLine, multiLine);
  }

  @Test
  public void testMethodBraceOnNextLineIfWrapped() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    formatter.getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.getSettings().RIGHT_MARGIN = 50;
    formatter.doClassTest(
      """
        public static void main(int state, int column, int width, int rate) {
        }
        """,
      """
        public static void main(int state, int column,
                                int width, int rate)
        {
        }
        """
    );
  }

  @Test
  public void testIDEA127110() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    formatter.doMethodTest(
      """
        if (   1 > 2) {

        } else {

        }

        try {

        } catch (     Exception e) {

        } finally {

        }""",
      """
        if (1 > 2) {

        } else {

        }

        try {

        } catch (Exception e) {

        } finally {

        }"""
    );
  }

  @Test
  public void testConstructorLeftBraceWithComment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    formatter.doClassTest(
      """
        /**
         *
         */
          public Test() {
        }
        """,
      """
        /**
         *
         */
        public Test() {
        }
        """
    );
  }

  @Test
  public void testConstructorLeftBraceWithAnnotation() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    formatter.doClassTest(
      """
           @Deprecated
        public Test() {
        }
        """,
      """
        @Deprecated
        public Test() {
        }
        """
    );
  }

  @Test
  public void testConstructorLeftBraceWithEndLineComment() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    formatter.doClassTest(
      """
        // comment
          public Test() {
        }
        """,
      """
        // comment
        public Test() {
        }
        """
    );
  }

  @Test
  public void testAnonClassCodeBlock_BracesIndented() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    formatter.doTextTest(
      """
        class X {
            public void run() {
                Runnable a = new Runnable() {
                    @Override
                    public void run() {
                       \s
                    }
                };
            }
        }""",

      """
        class X
            {
            public void run() {
                Runnable a = new Runnable()
                    {
                    @Override
                    public void run() {

                    }
                    };
            }
            }"""
    );
  }

  @Test
  public void testMethodIsSimple_IfCodeBlockHasNoLinefeeds() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.doClassTest(
      """
        public ModelAndView handleRequestInternalEmptyMulti(
                final HttpServletRequest httpServletRequest,
              final HttpServletResponse response)
              throws IOException {}""",
      """
        public ModelAndView handleRequestInternalEmptyMulti(
                final HttpServletRequest httpServletRequest,
                final HttpServletResponse response)
                throws IOException {}"""
    );
  }
  
  @Test
  public void testLambdaBrace() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    formatter.doMethodTest(
      "Runnable r = () -> {\n" +
      "};",
      """
        Runnable r = () ->
        {
        };"""
    );
  }
  
  @Test
  public void testLambdaBraceMoveToPrevLine() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    formatter.getSettings().KEEP_LINE_BREAKS = false;
    formatter.doMethodTest(
      """
        Runnable r = () ->
        {
        };""",
      "Runnable r = () -> {\n" +
      "};");
  }

  @Test
  public void testIdea149711() {
    JavaFormatterFixture formatter = formatterFixture.get();
    formatter.getSettings().WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    formatter.doMethodTest(
      "while (true);",

      "while (true) ;"
    );
  }

}
