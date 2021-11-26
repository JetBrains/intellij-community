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

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.java.LegacyChainedMethodCallsBlockBuilder;
import com.intellij.util.IncorrectOperationException;

/**
 * Is intended to hold java formatting indentation-specific tests.
 *
 * @author Denis Zhdanov
 */
public class JavaFormatterIndentationTest extends AbstractJavaFormatterTest {

  public void testClassInitializationBlockIndentation() {
    // Checking that initialization block body is correctly indented.
    doMethodTest(
      "checking(new Expectations() {{\n" +
      "one(tabConfiguration).addFilter(with(equal(PROPERTY)), with(aListContaining(\"a-c\")));\n" +
      "}});",
      "checking(new Expectations() {{\n" +
      "    one(tabConfiguration).addFilter(with(equal(PROPERTY)), with(aListContaining(\"a-c\")));\n" +
      "}});"
    );

    // Checking that closing curly brace of initialization block that is not the first block on a line is correctly indented.
    doTextTest("class Class {\n" + "    private Type field; {\n" + "    }\n" + "}",
               "class Class {\n" + "    private Type field;\n\n    {\n" + "    }\n" + "}");
    doTextTest(
      "class T {\n" +
      "    private final DecimalFormat fmt = new DecimalFormat(); {\n" +
      "        fmt.setGroupingUsed(false);\n" +
      "        fmt.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));\n" +
      "    }\n" +
      "}",
      "class T {\n" +
      "    private final DecimalFormat fmt = new DecimalFormat();\n\n    {\n" +
      "        fmt.setGroupingUsed(false);\n" +
      "        fmt.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));\n" +
      "    }\n" +
      "}"
    );
  }

  public void testNestedMethodsIndentation() {
    // Inspired by IDEA-43962

    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 4;

    doMethodTest(
      "BigDecimal.ONE\n" +
      "      .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE\n" +
      ".add(BigDecimal.ONE\n" +
      " .add(BigDecimal.ONE\n" +
      "  .add(BigDecimal.ONE\n" +
      " .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE)))))))));",
      "BigDecimal.ONE\n" +
      "    .add(BigDecimal.ONE\n" +
      "        .add(BigDecimal.ONE\n" +
      "            .add(BigDecimal.ONE\n" +
      "                .add(BigDecimal.ONE\n" +
      "                    .add(BigDecimal.ONE\n" +
      "                        .add(BigDecimal.ONE\n" +
      "                            .add(BigDecimal.ONE\n" +
      "                                .add(BigDecimal.ONE\n" +
      "                                    .add(BigDecimal.ONE)))))))));"
    );
  }

  public void testShiftedChainedIfElse() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
    getSettings().ELSE_ON_NEW_LINE = true;
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4;
    doMethodTest(
      "long a = System.currentTimeMillis();\n" +
      "    if (a == 0){\n" +
      "   }else if (a > 1){\n" +
      "  }else if (a > 2){\n" +
      " }else if (a > 3){\n" +
      "     }else if (a > 4){\n" +
      "      }else if (a > 5){\n" +
      "       }else{\n" +
      "        }",
      "long a = System.currentTimeMillis();\n" +
      "if (a == 0)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 1)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 2)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 3)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 4)\n" +
      "    {\n" +
      "    }\n" +
      "else if (a > 5)\n" +
      "    {\n" +
      "    }\n" +
      "else\n" +
      "    {\n" +
      "    }"
    );
  }

  public void testAlignedSubBlockIndentation() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().getRootSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 8;

    // Inspired by IDEA-54671
    doTextTest(
      "class Test {\n" +
      "    public void foo() {\n" +
      "        test(11\n" +
      "                     + 12\n" +
      "                     + 13,\n" +
      "             21\n" +
      "                     + 22\n" +
      "                     + 23\n" +
      "        )" +
      "    }\n" +
      "}",

      "class Test {\n" +
      "    public void foo() {\n" +
      "        test(11\n" +
      "                     + 12\n" +
      "                     + 13,\n" +
      "             21\n" +
      "                     + 22\n" +
      "                     + 23\n" +
      "        )\n" +
      "    }\n" +
      "}"
    );
  }

  public void testEnumIndentation() throws IncorrectOperationException {
    // Inspired by IDEADEV-2840
    doTextTest("enum Xyz {\n" + "FOO,\n" + "BAR,\n" + "}", "enum Xyz {\n" + "    FOO,\n" + "    BAR,\n" + "}");
  }

  public void testFirstColumnComment() throws IncorrectOperationException {
    // Inspired by IDEADEV-14116
    getSettings().KEEP_FIRST_COLUMN_COMMENT = false;

    doTextTest("class Foo{\n" + "private int foo;     // this is a foo\n" + "}",
               "class Foo {\n" + "    private int foo;     // this is a foo\n" + "}");
  }

  public void testCaseFromSwitch() throws IncorrectOperationException {
    // Inspired by IDEADEV-22920
    getSettings().INDENT_CASE_FROM_SWITCH = false;
    doTextTest(
      "class Foo{\n" +
      "void foo () {\n" +
      "switch(someValue) {\n" +
      " // This comment is correctly not-indented\n" +
      " case 1:\n" +
      "    doSomething();\n" +
      "    break;\n" +
      "\n" +
      " // This comment should not be indented, but it is\n" +
      " case 2:\n" +
      "    doSomethingElse();\n" +
      "    break;\n" +
      "}\n" +
      "}\n" +
      "}",

      "class Foo {\n" +
      "    void foo() {\n" +
      "        switch (someValue) {\n" +
      "        // This comment is correctly not-indented\n" +
      "        case 1:\n" +
      "            doSomething();\n" +
      "            break;\n" +
      "\n" +
      "        // This comment should not be indented, but it is\n" +
      "        case 2:\n" +
      "            doSomethingElse();\n" +
      "            break;\n" +
      "        }\n" +
      "    }\n" +
      "}");
  }

  public void testBinaryExpressionsWithRelativeIndents() {
    // Inspired by IDEA-21795
    getIndentOptions().USE_RELATIVE_INDENTS = true;
    getIndentOptions().CONTINUATION_INDENT_SIZE = 4;

    doTextTest(
      "public class FormattingTest {\n" +
      "\n" +
      "    public boolean test1(int a, int b, int c, int d) {\n" +
      "        return a == 1 &&\n" +
      "      b == 2;\n" +
      "    }\n" +
      "\n" +
      "    public boolean multilineSignOnCurrent(int a, int b, int c, int d) {\n" +
      "        return a == 0 &&\n" +
      "                                  (b == 0 ||\n" +
      "     c == 0) &&\n" +
      "  d == 0;\n" +
      "    }\n" +
      "\n" +
      "    public boolean multilineSignOnNext(int a, int b, int c, int d) {\n" +
      "        return a == 0\n" +
      "       && (b == 0\n" +
      "                                     || c == 0)\n" +
      "   && d == 0;\n" +
      "    }\n" +
      "\n" +
      "    public boolean expectedMultilineSignOnNext(int a, int b, int c, int d) {\n" +
      "        return a == 0\n" +
      "    && (b == 0\n" +
      "     || c == 0)\n" +
      "                       && d == 0;\n" +
      "    }\n" +
      "}",

      "public class FormattingTest {\n" +
      "\n" +
      "    public boolean test1(int a, int b, int c, int d) {\n" +
      "        return a == 1 &&\n" +
      "                   b == 2;\n" +
      "    }\n" +
      "\n" +
      "    public boolean multilineSignOnCurrent(int a, int b, int c, int d) {\n" +
      "        return a == 0 &&\n" +
      "                   (b == 0 ||\n" +
      "                        c == 0) &&\n" +
      "                   d == 0;\n" +
      "    }\n" +
      "\n" +
      "    public boolean multilineSignOnNext(int a, int b, int c, int d) {\n" +
      "        return a == 0\n" +
      "                   && (b == 0\n" +
      "                           || c == 0)\n" +
      "                   && d == 0;\n" +
      "    }\n" +
      "\n" +
      "    public boolean expectedMultilineSignOnNext(int a, int b, int c, int d) {\n" +
      "        return a == 0\n" +
      "                   && (b == 0\n" +
      "                           || c == 0)\n" +
      "                   && d == 0;\n" +
      "    }\n" +
      "}"
    );
  }
  
  public void testBracesShiftedOnNextLineOnMethodWithJavadoc() {
    // Inspired by IDEA-62997
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    
    String precededByJavadoc =
      "/**\n" +
      " * test\n" +
      " */\n" +
      "public int getFoo()\n" +
      "    {\n" +
      "    return foo;\n" +
      "    }";
    
    String precededBySingleLineComment =
      "// test\n" +
      "public int getFoo()\n" +
      "    {\n" +
      "    return foo;\n" +
      "    }";

    String precededByMultiLineComment =
      "/*\n" +
      "test\n" +
      "*/\n" +
      "public int getFoo()\n" +
      "    {\n" +
      "    return foo;\n" +
      "    }";
    
    doClassTest(precededByJavadoc, precededByJavadoc);
    doClassTest(precededBySingleLineComment, precededBySingleLineComment);
    doClassTest(precededByMultiLineComment, precededByMultiLineComment);
  }
  
  public void testAnonymousClassInstancesAsMethodCallArguments() {
    // Inspired by IDEA-65987
    
    doMethodTest(
      "foo(\"long string as the first argument\", new Runnable() {\n" +
      "public void run() {                         \n" +
      "}                                        \n" +
      "},                                            \n" +
      "new Runnable() {                         \n" +
      "public void run() {                 \n" +
      "}                                          \n" +
      "}                                             \n" +
      ");                                                       ",
      "foo(\"long string as the first argument\", new Runnable() {\n" +
      "            public void run() {\n" +
      "            }\n" +
      "        },\n" +
      "        new Runnable() {\n" +
      "            public void run() {\n" +
      "            }\n" +
      "        }\n" +
      ");"
    );
    
    doMethodTest(
      "foo(1,\n" +
      "2, new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}\n" +
      "});",
      "foo(1,\n" +
      "        2, new Runnable() {\n" +
      "            @Override\n" +
      "            public void run() {\n" +
      "            }\n" +
      "        });"
    );
    
    doMethodTest(
      "foo(new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}\n" +
      "},\n" +
      "new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}\n" +
      "});",
      "foo(new Runnable() {\n" +
      "        @Override\n" +
      "        public void run() {\n" +
      "        }\n" +
      "    },\n" +
      "        new Runnable() {\n" +
      "            @Override\n" +
      "            public void run() {\n" +
      "            }\n" +
      "        });"
    );
  }

  public void testAnonymousClassInstancesAsAlignedMethodCallArguments() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    doMethodTest(
      "foo(new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}\n" +
      "},\n" +
      "new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}\n" +
      "});",
      "foo(new Runnable() {\n" +
      "        @Override\n" +
      "        public void run() {\n" +
      "        }\n" +
      "    },\n" +
      "    new Runnable() {\n" +
      "        @Override\n" +
      "        public void run() {\n" +
      "        }\n" +
      "    });"
    );

    doMethodTest(
      "foo(123456789, new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}\n" +
      "},\n" +
      "new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}\n" +
      "});",
      "foo(123456789, new Runnable() {\n" +
      "        @Override\n" +
      "        public void run() {\n" +
      "        }\n" +
      "    },\n" +
      "    new Runnable() {\n" +
      "        @Override\n" +
      "        public void run() {\n" +
      "        }\n" +
      "    });"
    );

    doMethodTest(
      "foo(new Runnable() {\n" +
      "@Override\n" +
      "public void run() {\n" +
      "}" +
      "}, 1, 2);",
      "foo(new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "    }\n" +
      "}, 1, 2);"
    );
  }

  public void testAnonymousClassesOnSameLineAtMethodCallExpression() {
    doMethodTest(
      "foo(new Runnable() {\n" +
      "        public void run() {\n" +
      "        }\n" +
      "    }, new Runnable() {\n" +
      "               public void run() {\n" +
      "               }\n" +
      "              });",
      "foo(new Runnable() {\n" +
      "    public void run() {\n" +
      "    }\n" +
      "}, new Runnable() {\n" +
      "    public void run() {\n" +
      "    }\n" +
      "});"
    );
  }

  public void testAlignMultipleAnonymousClasses_PassedAsMethodParameters() {
    String text = "test(new Runnable() {\n" +
                  "    @Override\n" +
                  "    public void run() {\n" +
                  "        System.out.println(\"AAA!\");\n" +
                  "    }\n" +
                  "}, new Runnable() {\n" +
                  "    @Override\n" +
                  "    public void run() {\n" +
                  "        System.out.println(\"BBB!\");\n" +
                  "    }\n" +
                  "});\n";
    doMethodTest(text, text);
  }

  public void testAlignmentAdditionalParamsWithMultipleAnonymousClasses_PassedAsMethodParameters() {
    String text = "foo(1221, new Runnable() {\n" +
                  "    @Override\n" +
                  "    public void run() {\n" +
                  "        System.out.println(\"A\");\n" +
                  "    }\n" +
                  "}, new Runnable() {\n" +
                  "    @Override\n" +
                  "    public void run() {\n" +
                  "        System.out.println(\"BB\");\n" +
                  "    }\n" +
                  "});";
    doMethodTest(text, text);
  }

  public void testAlignmentMultipleParamsWithAnonymousClass_PassedAsMethodParams() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text = "test(1000,\n" +
                  "     new Runnable() {\n" +
                  "         @Override\n" +
                  "         public void run() {\n" +
                  "             System.out.println(\"BBB\");\n" +
                  "         }\n" +
                  "     }\n" +
                  ");";
    doMethodTest(text, text);
  }

  public void testAlignmentMultipleAnonymousClassesOnNewLines() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text = "test(1000,\n" +
                  "     new Runnable() {\n" +
                  "         @Override\n" +
                  "         public void run() {\n" +
                  "             System.out.println(\"BBB\");\n" +
                  "         }\n" +
                  "     },\n" +
                  "     new Runnable() {\n" +
                  "         @Override\n" +
                  "         public void run() {\n" +
                  "             System.out.println(\"BBB\");\n" +
                  "         }\n" +
                  "     }\n" +
                  ");";
    doMethodTest(text, text);
  }

  public void testEnforceChildrenIndent_OfAnonymousClasses_IfAnyOfParamsIsLocatedOnNewLine() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    String text = "test(\"Suuuuuuuuuuuuuuuuuper loooooooooooong string\",\n" +
                  "     \"Next loooooooooooooooooooooong striiiiiiiiiiing\", new Runnable() {\n" +
                  "            @Override\n" +
                  "            public void run() {\n" +
                  "\n" +
                  "            }\n" +
                  "        }, new Runnable() {\n" +
                  "            @Override\n" +
                  "            public void run() {\n" +
                  "\n" +
                  "            }\n" +
                  "        }\n" +
                  ");\n";
    doMethodTest(text, text);
  }

  public void testPackagePrivateAnnotation() {
    // Inspired by IDEA-67294
    
    String text = 
      "@Retention(RUNTIME)\n" +
      "@Target({FIELD, PARAMETER, METHOD})\n" +
      "@interface MyAnnotation {\n" +
      "\n" +
      "}";
    doTextTest(text, text);
  }

  public void testIncompleteMethodCall() {
    // Inspired by IDEA-79836.

    doMethodTest(
      "test(new Runnable() {\n" +
      "         public void run() {\n" +
      "         }\n" +
      "     }, new Runnable() {\n" +
      "         public void run() {\n" +
      "         }\n" +
      "     }, )",
      "test(new Runnable() {\n" +
      "    public void run() {\n" +
      "    }\n" +
      "}, new Runnable() {\n" +
      "    public void run() {\n" +
      "    }\n" +
      "}, )"
    );
  }

  public void testCStyleCommentIsNotMoved() {
    // IDEA-87087
    doClassTest(
      "                /*\n" +
      "                   this is a c-style comment\n" +
      "                 */\n" +
      "           // This is a line comment",
      "            /*\n" +
      "               this is a c-style comment\n" +
      "             */\n" +
      "// This is a line comment"
    );
  }

  public void testMultilineCommentAtFileStart() {
    // IDEA-90860
    String text =
      "\n" +
      "/*\n" +
      " * comment\n" +
      " */\n" +
      "\n" +
      "class Test {\n" +
      "}";
    doTextTest(text, text);
  }

  public void testMultilineCommentAndTabsIndent() {
    // IDEA-91703
    String initial = 
      "\t/*\n" +
      "\t\t* comment\n" +
      "\t */\n" +
      "class Test {\n" +
      "}";

    String expected =
      "/*\n" +
      " * comment\n" +
      " */\n" +
      "class Test {\n" +
      "}";
    
    getIndentOptions().USE_TAB_CHARACTER = true;
    doTextTest(initial, expected);
  }

  public void testLambdaIndentation() {
    String before = "Runnable r = () ->\n" +
                    "{\n" +
                    "    System.out.println(\"olo\");\n" +
                    "};";
    doMethodTest(before, before);
  }
  
  public void testAnnotatedParameters() {
    // it is supposed that this
    getJavaSettings().DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION_IN_PARAMETER = true;
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().RIGHT_MARGIN = 120;
    getSettings().WRAP_LONG_LINES = true;
    String before = "public class Formatting {\n" +
                    "  @RequestMapping(value = \"/\", method = GET)\n" +
                    "  public HttpEntity<String> helloWorld(@RequestParam(\"name\") String name, @PageableDefault(page = 0, size = 10) Pageable pageable) {\n" +
                    "    // I'd expect the line above to be indented by 4 spaces\n" +
                    "    return ResponseEntity.ok(\"Hello \" + name);\n" +
                    "  }\n" +
                    "}";
    String after = "public class Formatting {\n" +
                   "    @RequestMapping(value = \"/\", method = GET)\n" +
                   "    public HttpEntity<String> helloWorld(@RequestParam(\"name\") String name,\n" +
                   "                                         @PageableDefault(page = 0, size = 10) Pageable pageable) {\n" +
                   "        // I'd expect the line above to be indented by 4 spaces\n" +
                   "        return ResponseEntity.ok(\"Hello \" + name);\n" +
                   "    }\n" +
                   "}";
    
    doTextTest(before, after);
  }

  public void testTextBlock() {
    String before = "class Formatting {\n" +
                    "    void test() {\n" +
                    "        String block = \"\"\"\n" +
                    "                             \n" +
                    " text\n" +
                    "block\"\"\";\n" +
                    "  " +
                    "  }\n" +
                    "}";

    String after = "class Formatting {\n" +
                   "    void test() {\n" +
                   "        String block = \"\"\"\n" +
                   "                                             \n" +
                   "                 text\n" +
                   "                block\"\"\";\n" +
                   "    " +
                   "}\n" +
                   "}";
    doTextTest(before, after);
  }


  public void testKeepBuilderMethodsIndents() {
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().BUILDER_METHODS = "wrap,flowPanel,widget,builder,end";
    getSettings().KEEP_BUILDER_METHODS_INDENTS = true;

    doTextTest(
      "class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        PanelBuilder.wrap(getCenterPanel(), \"review-view\")\n" +
      "            .flowPanel(\"sidebar-offset\")   //content\n" +
      "              .widget(myReviewHints)\n" +
      "              .flowPanel(\"island\")          //changes island\n" +
      "                .flowPanel(\"pane-toolbar pane-toolbar_island clearfix\") //paneToolbar\n" +
      "                  .flowPanel(\"pane-toolbar__left pane-toolbar__left_header\") //paneToolbarLeft\n" +
      "                    .widget(reviewStateLabel(reviewDescriptorSignal))\n" +
      "                    .widget(reviewIdLabel(reviewDescriptorSignal))\n" +
      "                    .builder(reviewTitle(projectDescriptor, reviewDescriptorSignal))\n" +
      "                  .end()\n" +
      "                .end()\n" +
      "                .flowPanel(\"revision-files-standalone\") // review changes view\n" +
      "                  .widget(myChangesListView)\n" +
      "                .end()\n" +
      "              .end()\n" +
      "              .widget(myReviewFeedView)\n" +
      "            .end();\n" +
      "    }\n" +
      "}",

      "class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        PanelBuilder.wrap(getCenterPanel(), \"review-view\")\n" +
      "                .flowPanel(\"sidebar-offset\")   //content\n" +
      "                  .widget(myReviewHints)\n" +
      "                  .flowPanel(\"island\")          //changes island\n" +
      "                    .flowPanel(\"pane-toolbar pane-toolbar_island clearfix\") //paneToolbar\n" +
      "                      .flowPanel(\"pane-toolbar__left pane-toolbar__left_header\") //paneToolbarLeft\n" +
      "                        .widget(reviewStateLabel(reviewDescriptorSignal))\n" +
      "                        .widget(reviewIdLabel(reviewDescriptorSignal))\n" +
      "                        .builder(reviewTitle(projectDescriptor, reviewDescriptorSignal))\n" +
      "                      .end()\n" +
      "                    .end()\n" +
      "                    .flowPanel(\"revision-files-standalone\") // review changes view\n" +
      "                      .widget(myChangesListView)\n" +
      "                    .end()\n" +
      "                  .end()\n" +
      "                  .widget(myReviewFeedView)\n" +
      "                .end();\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea158691() {
    doMethodTest(
      "context.start(\n" +
      "            first,\n" +
      "            second)\n" +
      "            .setPriority(1)\n" +
      "            .build();",

      "context.start(\n" +
      "                first,\n" +
      "                second)\n" +
      "        .setPriority(1)\n" +
      "        .build();"
    );
  }

  public void testIdea274755() {
    getSettings().getIndentOptions().USE_RELATIVE_INDENTS = true;
    doMethodTest(
      "public class Test {\n" +
      "void test() {\n" +
      "    final var command = CreateUpload.builder()\n" +
      ".identityId(userId)\n" +
      "      .iotId(iotId)\n" +
      ".build();\n" +
      "   }\n" +
      "}",

      "public class Test {\n" +
      "    void test() {\n" +
      "        final var command = CreateUpload.builder()\n" +
      "                                    .identityId(userId)\n" +
      "                                    .iotId(iotId)\n" +
      "                                    .build();\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea274778() {
    CommonCodeStyleSettings.IndentOptions indentOptions = getSettings().getIndentOptions();
    indentOptions.INDENT_SIZE = 3;
    indentOptions.CONTINUATION_INDENT_SIZE = 3;
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    RegistryValue pre212compat = Registry.get(LegacyChainedMethodCallsBlockBuilder.COMPATIBILITY_KEY);
    try {
      pre212compat.setValue(true);
      doTextTest(
        "class Foo {\n" +
        "void foo() {\n" +
        "LOG.error(DetailsMessage.of(\n" +
        "\"TITLE\",\n" +
        "\"LONG MESSAGE TEXT...\")\n" +
        ".with(\"value\", value));\n" +
        "}\n" +
        "}",

        "class Foo {\n" +
        "   void foo() {\n" +
        "      LOG.error(DetailsMessage.of(\n" +
        "         \"TITLE\",\n" +
        "         \"LONG MESSAGE TEXT...\")\n" +
        "                              .with(\"value\", value));\n" +
        "   }\n" +
        "}"
      );
    }
    finally {
      pre212compat.setValue(false);
    }
  }
}
