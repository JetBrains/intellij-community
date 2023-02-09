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

/**
 * Is intended to hold specific java formatting tests for 'braces placement' settings (
 * {@code Project Settings - Code Style - Alignment and Braces}).
 */
public class JavaFormatterBracesTest extends AbstractJavaFormatterTest {

  public void testBracePositioningAtPreviousLine() {
    // Inspired by IDEADEV-18529
    doTextTest(
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

  public void testSimpleBlockInOneLinesAndForceBraces() {
    // Inspired by IDEA-19328
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    getSettings().SPACE_WITHIN_BRACES = true;

    doMethodTest(
      "if (x > y) System.out.println(\"foo!\");",

      "if (x > y) { System.out.println(\"foo!\"); }"
    );
  }

  public void testEnforcingBracesForExpressionEndingWithLineComment() {
    // Inspired by IDEA-57936
    getSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    doMethodTest(
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

  public void testMoveBraceOnNextLineForAnnotatedMethod() {
    // Inspired by IDEA-59336
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;

    doClassTest(
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
  
  public void testKeepSimpleClassesAndInterfacesInOneLine() {
    // Inspired by IDEA-65433
    getSettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    
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
      doTextTest(test, test);
    }
  }

  public void testKeepSimpleClassesInOneLineAndLeftBraceOnNextLine() {
    // Inspired by IDEA-75053.
    getSettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    
    String text =
      """
        class Test
        {
            void foo() {
                bind(new TypeLiteral<MyType>() {}).toProvider(MyProvider.class);
            }
        }""";
    doTextTest(text, text);
  }

  public void testSimpleMethodsInOneLineEvenIfExceedsRightMargin() {
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().RIGHT_MARGIN = 90;
    getSettings().SPACE_WITHIN_BRACES = true;
    String text = """
      public class Repr2 {
          public void start() { System.out.println("kfjsdkfjsdkfjskdjfslkdjfklsdjfklsdjfksjdfkljsdkfjsd!"); }
      }""";
    doTextTest(text, text);
  }

  public void testKeepSimpleBlocksInOneLine_OnIfStatementsThenBlock() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;
    String singleLine = "if (2 > 3) { System.out.println(\"AA!\"); }";

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doMethodTest(singleLine, singleLine);

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doMethodTest(singleLine, singleLine);
  }

  public void testKeepSimpleBlocksInOneLine_OnIfStatementsElseBlock() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;

    String before = """
      if (2 > 3) {
          System.out.println("AA!");
      } else { int a = 3; }""";

    String afterNextLineOption = """
      if (2 > 3)
      {
          System.out.println("AA!");
      } else { int a = 3; }""";

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doMethodTest(before, afterNextLineOption);

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doMethodTest(before, before);
  }

  public void testIfStatement_WhenBraceOnNextLine_AndKeepSimpleBlockInOneLineEnabled() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    String before = """
      if (2 > 3) {
          System.out.println("AA!");
      }""";
    String after = """
      if (2 > 3)
      {
          System.out.println("AA!");
      }""";
    doMethodTest(before, after);
  }

  public void testIfStatementElseBranchIsOnNewLine() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
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
    doMethodTest(before, after);
  }

  public void testIfElseBranchesKeepedInOneLine() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().SPACE_WITHIN_BRACES = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;

    String singleLine = "if (2 > 3) { System.out.println(\"AA!\"); } else { System.out.println(\"BBB!!\"); }";
    String multiLine = "if (2 > 3) { System.out.println(\"AA!\"); }\n" +
                       "else { System.out.println(\"BBB!!\"); }";

    getSettings().ELSE_ON_NEW_LINE = false;
    doMethodTest(singleLine, singleLine);
    doMethodTest(multiLine, singleLine);

    getSettings().ELSE_ON_NEW_LINE = true;
    doMethodTest(singleLine, multiLine);
    doMethodTest(multiLine, multiLine);
  }

  public void testMethodBraceOnNextLineIfWrapped() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().RIGHT_MARGIN = 50;
    doClassTest(
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

  public void testIDEA127110() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doMethodTest(
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

  public void testConstructorLeftBraceWithComment() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doClassTest(
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

  public void testConstructorLeftBraceWithAnnotation() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doClassTest(
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

  public void testConstructorLeftBraceWithEndLineComment() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doClassTest(
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

  public void testAnonClassCodeBlock_BracesIndented() {
    getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    doTextTest(
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

  public void testMethodIsSimple_IfCodeBlockHasNoLinefeeds() {
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    doClassTest(
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
  
  public void testLambdaBrace() {
    getSettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doMethodTest(
      "Runnable r = () -> {\n" +
      "};",
      """
        Runnable r = () ->
        {
        };"""
    );
  }
  
  public void testLambdaBraceMoveToPrevLine() {
    getSettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getSettings().KEEP_LINE_BREAKS = false;
    doMethodTest(
      """
        Runnable r = () ->
        {
        };""",
      "Runnable r = () -> {\n" +
      "};");
  }

  public void testIdea149711() {
    getSettings().WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    doMethodTest(
      "while (true);",

      "while (true) ;"
    );
  }

}
