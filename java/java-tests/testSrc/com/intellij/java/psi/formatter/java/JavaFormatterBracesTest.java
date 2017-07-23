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
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:39:24 PM
 */
public class JavaFormatterBracesTest extends AbstractJavaFormatterTest {

  public void testBracePositioningAtPreviousLine() throws Exception {
    // Inspired by IDEADEV-18529
    doTextTest(
      "public class TestBed\n" +
      "{\n" +
      "    public void methodOne()\n" +
      "    {\n" +
      "        //code...\n" +
      "    }\n" +
      "\n" +
      "    @SomeAnnotation\n" +
      "            <T extends Comparable> void methodTwo(T item) {\n" +
      "        //code...\n" +
      "    }\n" +
      "\n" +
      "    private void methodThree(String s) {\n" +
      "        //code...\n" +
      "    }\n" +
      "}",

      "public class TestBed {\n" +
      "    public void methodOne() {\n" +
      "        //code...\n" +
      "    }\n" +
      "\n" +
      "    @SomeAnnotation\n" +
      "    <T extends Comparable> void methodTwo(T item) {\n" +
      "        //code...\n" +
      "    }\n" +
      "\n" +
      "    private void methodThree(String s) {\n" +
      "        //code...\n" +
      "    }\n" +
      "}");
  }

  public void testSimpleBlockInOneLinesAndForceBraces() throws Exception {
    // Inspired by IDEA-19328
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    doMethodTest(
      "if (x > y) System.out.println(\"foo!\");",

      "if (x > y) { System.out.println(\"foo!\"); }"
    );
  }

  public void testEnforcingBracesForExpressionEndingWithLineComment() throws Exception {
    // Inspired by IDEA-57936
    getSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;

    doMethodTest(
      "if (true) i = 1; // Cool if\n" +
      "else i = 2;",

      "if (true) {\n" +
      "    i = 1; // Cool if\n" +
      "} else {\n" +
      "    i = 2;\n" +
      "}"
    );
  }

  public void testMoveBraceOnNextLineForAnnotatedMethod() throws Exception {
    // Inspired by IDEA-59336
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;

    doClassTest(
      "@Override\n" +
      "public int hashCode() {\n" +
      "}\n" +
      "@Deprecated\n" +
      "void foo() {\n" +
      "}",
      "@Override\n" +
      "public int hashCode()\n" +
      "{\n" +
      "}\n" +
      "\n" +
      "@Deprecated\n" +
      "void foo()\n" +
      "{\n" +
      "}"
    );
  }
  
  public void testKeepSimpleClassesAndInterfacesInOneLine() {
    // Inspired by IDEA-65433
    getSettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    
    String[] tests = {
      "class Test {}",
      
      "interface Test {}",
      
      "class Test {\n" +
      "    void test() {\n" +
      "        new Object() {};\n" +
      "    }\n" +
      "}",
      
      "class Test {\n" +
      "    void test() {\n" +
      "        bind(new TypeLiteral<MyType>() {}).toProvider(MyProvider.class);\n" +
      "    }\n" +
      "}"
    };

    for (String test : tests) {
      doTextTest(test, test);
    }
  }

  public void testKeepSimpleClassesInOneLineAndLeftBraceOnNextLine() throws Exception {
    // Inspired by IDEA-75053.
    getSettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = true;
    getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    
    String text =
      "class Test\n" +
      "{\n" +
      "    void foo() {\n" +
      "        bind(new TypeLiteral<MyType>() {}).toProvider(MyProvider.class);\n" +
      "    }\n" +
      "}";
    doTextTest(text, text);
  }

  public void testSimpleMethodsInOneLineEvenIfExceedsRightMargin() {
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().RIGHT_MARGIN = 90;
    String text = "public class Repr2 {\n" +
                  "    public void start() { System.out.println(\"kfjsdkfjsdkfjskdjfslkdjfklsdjfklsdjfksjdfkljsdkfjsd!\"); }\n" +
                  "}";
    doTextTest(text, text);
  }

  public void testKeepSimpleBlocksInOneLine_OnIfStatementsThenBlock() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    String singleLine = "if (2 > 3) { System.out.println(\"AA!\"); }";

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doMethodTest(singleLine, singleLine);

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doMethodTest(singleLine, singleLine);
  }

  public void testKeepSimpleBlocksInOneLine_OnIfStatementsElseBlock() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    String before = "if (2 > 3) {\n" +
                    "    System.out.println(\"AA!\");\n" +
                    "} else { int a = 3; }";

    String afterNextLineOption = "if (2 > 3)\n" +
                                 "{\n" +
                                 "    System.out.println(\"AA!\");\n" +
                                 "} else { int a = 3; }";

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doMethodTest(before, afterNextLineOption);

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doMethodTest(before, before);
  }

  public void testIfStatement_WhenBraceOnNextLine_AndKeepSimpleBlockInOneLineEnabled() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    String before = "if (2 > 3) {\n" +
                    "    System.out.println(\"AA!\");\n" +
                    "}";
    String after = "if (2 > 3)\n" +
                   "{\n" +
                   "    System.out.println(\"AA!\");\n" +
                   "}";
    doMethodTest(before, after);
  }

  public void testIfStatementElseBranchIsOnNewLine() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    String before = "if (2 > 3) {\n" +
                    "    System.out.println(\"AA!\");\n" +
                    "} else {\n" +
                    "    int a = 3;\n" +
                    "}";
    String after = "if (2 > 3)\n" +
                   "{\n" +
                   "    System.out.println(\"AA!\");\n" +
                   "} else\n" +
                   "{\n" +
                   "    int a = 3;\n" +
                   "}";
    doMethodTest(before, after);
  }

  public void testIfElseBranchesKeepedInOneLine() throws Exception {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
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
      "public static void main(int state, int column, int width, int rate) {\n" +
      "}\n",
      "public static void main(int state, int column,\n" +
      "                        int width, int rate)\n" +
      "{\n" +
      "}\n"
    );
  }

  public void testIDEA127110() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doMethodTest(
      "if (   1 > 2) {\n" +
      "\n" +
      "} else {\n" +
      "\n" +
      "}\n" +
      "\n" +
      "try {\n" +
      "\n" +
      "} catch (     Exception e) {\n" +
      "\n" +
      "} finally {\n" +
      "\n" +
      "}",
      "if (1 > 2) {\n" +
      "\n" +
      "} else {\n" +
      "\n" +
      "}\n" +
      "\n" +
      "try {\n" +
      "\n" +
      "} catch (Exception e) {\n" +
      "\n" +
      "} finally {\n" +
      "\n" +
      "}"
    );
  }

  public void testConstructorLeftBraceWithComment() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doClassTest(
      "/**\n" +
      " *\n" +
      " */\n" +
      "  public Test() {\n" +
      "}\n",
      "/**\n" +
      " *\n" +
      " */\n" +
      "public Test() {\n" +
      "}\n"
    );
  }

  public void testConstructorLeftBraceWithAnnotation() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doClassTest(
      "   @Deprecated\n" +
      "public Test() {\n" +
      "}\n",
      "@Deprecated\n" +
      "public Test() {\n" +
      "}\n"
    );
  }

  public void testConstructorLeftBraceWithEndLineComment() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doClassTest(
      "// comment\n" +
      "  public Test() {\n" +
      "}\n",
      "// comment\n" +
      "public Test() {\n" +
      "}\n"
    );
  }

  public void testAnonClassCodeBlock_BracesIndented() {
    getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    doTextTest(
      "class X {\n" +
      "    public void run() {\n" +
      "        Runnable a = new Runnable() {\n" +
      "            @Override\n" +
      "            public void run() {\n" +
      "                \n" +
      "            }\n" +
      "        };\n" +
      "    }\n" +
      "}",

      "class X\n" +
      "    {\n" +
      "    public void run() {\n" +
      "        Runnable a = new Runnable()\n" +
      "            {\n" +
      "            @Override\n" +
      "            public void run() {\n" +
      "\n" +
      "            }\n" +
      "            };\n" +
      "    }\n" +
      "    }"
    );
  }

  public void testMethodIsSimple_IfCodeBlockHasNoLinefeeds() {
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    doClassTest(
      "public ModelAndView handleRequestInternalEmptyMulti(\n" +
      "        final HttpServletRequest httpServletRequest,\n" +
      "      final HttpServletResponse response)\n" +
      "      throws IOException {}",
      "public ModelAndView handleRequestInternalEmptyMulti(\n" +
      "        final HttpServletRequest httpServletRequest,\n" +
      "        final HttpServletResponse response)\n" +
      "        throws IOException {}"
    );
  }
  
  public void testLambdaBrace() {
    getSettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doMethodTest(
      "Runnable r = () -> {\n" +
      "};",
      "Runnable r = () ->\n" +
      "{\n" +
      "};"
    );
  }
  
  public void testLambdaBraceMoveToPrevLine() {
    getSettings().LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getSettings().KEEP_LINE_BREAKS = false;
    doMethodTest(
      "Runnable r = () ->\n" + 
      "{\n" +
      "};", 
      "Runnable r = () -> {\n" +
      "};");
  }

}
