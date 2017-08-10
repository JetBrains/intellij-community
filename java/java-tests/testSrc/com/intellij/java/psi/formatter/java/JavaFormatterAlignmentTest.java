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

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;

import static com.intellij.formatting.FormatterTestUtils.Action.REFORMAT_WITH_CONTEXT;

/**
 * Is intended to hold specific java formatting tests for alignment settings (
 * {@code Project Settings - Code Style - Alignment and Braces}).
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:42:00 PM
 */
public class JavaFormatterAlignmentTest extends AbstractJavaFormatterTest {

  public void testChainedMethodsAlignment() {
    // Inspired by IDEA-30369
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 8;
    doTest();
  }

  public void testMethodAndChainedField() {
    // Inspired by IDEA-79806

    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doMethodTest(
      "Holder.INSTANCE\n" +
      "                .foo();",
      "Holder.INSTANCE\n" +
      "        .foo();"
    );
  }
  
  public void testChainedMethodWithComments() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doMethodTest("AAAAA.b()\n" +
                 ".c() // comment after line\n" +
                 ".d()\n" +
                 ".e();",

                 "AAAAA.b()\n" +
                 "     .c() // comment after line\n" +
                 "     .d()\n" +
                 "     .e();");
  }

  public void testChainedMethodWithBlockComment() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTextTest("class X {\n" +
               "    public void test() {\n" +
               "        AAAAAA.b()\n" +
               ".c()\n" +
               ".d()\n" +
               "          /* simple block comment */\n" +
               ".e();\n" +
               "    }\n" +
               "}",
               "class X {\n" +
               "    public void test() {\n" +
               "        AAAAAA.b()\n" +
               "              .c()\n" +
               "              .d()\n" +
               "              /* simple block comment */\n" +
               "              .e();\n" +
               "    }\n" +
               "}");
  }
  
  public void testMultipleMethodAnnotationsCommentedInTheMiddle() {
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 1;
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 4;

    // Inspired by IDEA-53942
    doTextTest(
      "public class Test {\n" +
      "          @Override\n" +
      "//       @XmlElement(name = \"Document\", required = true, type = DocumentType.class)\n" +
      "       @XmlTransient\n" +
      "  void foo() {\n" +
      "}\n" +
      "}",

      "public class Test {\n" +
      "\n" +
      "    @Override\n" +
      "//       @XmlElement(name = \"Document\", required = true, type = DocumentType.class)\n" +
      "    @XmlTransient\n" +
      "    void foo() {\n" +
      "    }\n" +
      "}"
    );
  }

  public void testTernaryOperator() {
    // Inspired by IDEADEV-13018
    getSettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;

    doMethodTest("int i = a ? x\n" + ": y;", "int i = a ? x\n" + "          : y;");
  }

  public void testMethodCallArgumentsAndSmartTabs() throws IncorrectOperationException {
    // Inspired by IDEADEV-20144.
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).SMART_TABS = true;
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).USE_TAB_CHARACTER = true;
    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "        bar(new Object[] {\n" +
               "            \"hello1\",\n" +
               "            \"hello2\", add(\"hello3\",\n" +
               "                           \"world\")\n" +
               "});" +
               "    }}", "class Foo {\n" +
                         "\tvoid foo() {\n" +
                         "\t\tbar(new Object[]{\n" +
                         "\t\t\t\t\"hello1\",\n" +
                         "\t\t\t\t\"hello2\", add(\"hello3\",\n" +
                         "\t\t\t\t              \"world\")\n" +
                         "\t\t});\n" +
                         "\t}\n" +
                         "}");
  }

  public void testArrayInitializer() throws IncorrectOperationException {
    // Inspired by IDEADEV-16136
    getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doTextTest(
      "@SuppressWarnings({\"UseOfSystemOutOrSystemErr\", \"AssignmentToCollectionOrArrayFieldFromParameter\", \"ReturnOfCollectionOrArrayField\"})\n" +
      "public class Some {\n" +
      "}",
      "@SuppressWarnings({\"UseOfSystemOutOrSystemErr\",\n" +
      "                   \"AssignmentToCollectionOrArrayFieldFromParameter\",\n" +
      "                   \"ReturnOfCollectionOrArrayField\"})\n" +
      "public class Some {\n" +
      "}");
  }

  public void testMethodBrackets() {
    // Inspired by IDEA-53013
    getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = true;
    getSettings().ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
    getSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getSettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;

    doClassTest(
      "public void foo(int i,\n" +
      "                  int j) {\n" +
      "}\n" +
      "\n" +
      "  public void bar() {\n" +
      "    foo(1,\n" +
      "        2);\n" +
      "  }",

      "public void foo(int i,\n" +
      "                int j\n" +
      "               ) {\n" +
      "}\n" +
      "\n" +
      "public void bar() {\n" +
      "    foo(1,\n" +
      "        2\n" +
      "       );\n" +
      "}"
    );

    // Inspired by IDEA-55306
    getSettings().ALIGN_MULTILINE_METHOD_BRACKETS = false;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
    String method =
      "executeCommand(new Command<Boolean>() {\n" +
      "    public Boolean run() throws ExecutionException {\n" +
      "        return doInterrupt();\n" +
      "    }\n" +
      "});";
    doMethodTest(method, method);
  }

  public void testFieldInColumnsAlignment() {
    // Inspired by IDEA-55147
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    getSettings().FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings().VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    doTextTest(
      "public class FormattingTest {\n" +
      "\n" +
      "    int start = 1;\n" +
      "    double end = 2;\n" +
      "\n" +
      "    int i2 = 1;\n" +
      "    double dd2,\n" +
      "        dd3 = 2;\n" +
      "\n" +
      "    // asd\n" +
      "    char ccc3 = 'a';\n" +
      "    double ddd31, ddd32 = 1;\n" +
      "\n" +
      "    private\n" +
      "    final String s4 = \"\";\n" +
      "    private\n" +
      "    transient int i4 = 1;\n" +
      "\n" +
      "    private final String s5 = \"xxx\";\n" +
      "    private transient int iiii5 = 1;\n" +
      "    /*sdf*/\n" +
      "    @MyAnnotation(value = 1, text = 2) float f5 = 1;\n" +
      "}",

      "public class FormattingTest {\n" +
      "\n" +
      "    int    start = 1;\n" +
      "    double end   = 2;\n" +
      "\n" +
      "    int    i2   = 1;\n" +
      "    double dd2,\n" +
      "            dd3 = 2;\n" +
      "\n" +
      "    // asd\n" +
      "    char   ccc3         = 'a';\n" +
      "    double ddd31, ddd32 = 1;\n" +
      "\n" +
      "    private\n" +
      "    final     String s4 = \"\";\n" +
      "    private\n" +
      "    transient int    i4 = 1;\n" +
      "\n" +
      "    private final                      String s5    = \"xxx\";\n" +
      "    private transient                  int    iiii5 = 1;\n" +
      "    /*sdf*/\n" +
      "    @MyAnnotation(value = 1, text = 2) float  f5    = 1;\n" +
      "}"
    );
  }

  public void testTabsAndFieldsInColumnsAlignment() {
    // Inspired by IDEA-56242
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    getIndentOptions().USE_TAB_CHARACTER = true;

    doTextTest(
      "public class Test {\n" +
        "\tprivate Long field2 = null;\n" +
        "\tprivate final Object field1 = null;\n" +
        "\tprivate int i = 1;\n" +
      "}",

      "public class Test {\n" +
        "\tprivate       Long   field2 = null;\n" +
        "\tprivate final Object field1 = null;\n" +
        "\tprivate       int    i      = 1;\n" +
      "}"
    );
  }

  public void testDoNotAlignIfNotEnabled() {
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = false;
    doTextTest(
      "public class Test {\n" +
      "private Long field2 = null;\n" +
      "private final Object field1 = null;\n" +
      "private int i = 1;\n" +
      "}",

      "public class Test {\n" +
      "    private Long field2 = null;\n" +
      "    private final Object field1 = null;\n" +
      "    private int i = 1;\n" +
      "}"
    );
  }

  public void testAnnotatedAndNonAnnotatedFieldsInColumnsAlignment() {
    // Inspired by IDEA-60237

    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTextTest(
      "public class Test {\n" +
      "    @Id\n" +
      "    private final String name;\n" +
      "    @Column(length = 2 * 1024 * 1024 /* 2 MB */)\n" +
      "    private String value;\n" +
      "    private boolean required;\n" +
      "    private String unsetValue;\n" +
      "}",

      "public class Test {\n" +
      "    @Id\n" +
      "    private final String  name;\n" +
      "    @Column(length = 2 * 1024 * 1024 /* 2 MB */)\n" +
      "    private       String  value;\n" +
      "    private       boolean required;\n" +
      "    private       String  unsetValue;\n" +
      "}"
    );
  }

  public void testAlignThrowsKeyword() {
    // Inspired by IDEA-63820

    getSettings().ALIGN_THROWS_KEYWORD = true;
    doClassTest(
      "public void test()\n" +
      "                 throws Exception {}",
      "public void test()\n" +
      "throws Exception {\n" +
      "}"
    );

    getSettings().ALIGN_THROWS_KEYWORD = false;
    doClassTest(
      "public void test()\n" +
      "                 throws Exception {}",
      "public void test()\n" +
      "        throws Exception {\n" +
      "}"
    );
  }

  public void testAlignResourceList() {
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;

    getSettings().ALIGN_MULTILINE_RESOURCES = true;
    doMethodTest("try (MyResource r1 = null;\n" +
                 "MyResource r2 = null) { }",
                 "try (MyResource r1 = null;\n" +
                 "     MyResource r2 = null) { }");

    getSettings().ALIGN_MULTILINE_RESOURCES = false;
    doMethodTest("try (MyResource r1 = null;\n" +
                 "MyResource r2 = null) { }",
                 "try (MyResource r1 = null;\n" +
                 "        MyResource r2 = null) { }");
  }


  public void testChainedMethodCallsAfterFieldsChain_WithAlignment() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doMethodTest(
      "a.current.current.current.getThis().getThis().getThis();",

      "a.current.current.current.getThis()\n" +
      "                         .getThis()\n" +
      "                         .getThis();"
    );

    doMethodTest(
      "a.current.current.current.getThis().getThis().getThis().current.getThis().getThis().getThis().getThis();",

      "a.current.current.current.getThis()\n" +
      "                         .getThis()\n" +
      "                         .getThis().current.getThis()\n" +
      "                                           .getThis()\n" +
      "                                           .getThis()\n" +
      "                                           .getThis();"
    );


    String onlyMethodCalls = "getThis().getThis().getThis();";
    String formatedMethodCalls = "getThis().getThis()\n" +
                                 "         .getThis();";

    doMethodTest(onlyMethodCalls, formatedMethodCalls);
  }

  public void testChainedMethodCallsAfterFieldsChain_WithoutAlignment() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = false;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doMethodTest(
      "a.current.current.current.getThis().getThis().getThis();",

      "a.current.current.current.getThis()\n" +
      "        .getThis()\n" +
      "        .getThis();"
    );
  }

  public void testChainedMethodCalls_WithChopDownIfLongOption() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM; // it's equal to "Chop down if long"
    getSettings().RIGHT_MARGIN = 50;

    String before = "a.current.current.getThis().getThis().getThis().getThis().getThis();";
    doMethodTest(
      before,
      "a.current.current.getThis()\n" +
      "                 .getThis()\n" +
      "                 .getThis()\n" +
      "                 .getThis()\n" +
      "                 .getThis();"
    );

    getSettings().RIGHT_MARGIN = 80;
    doMethodTest(before, before);
  }

  public void testChainedMethodCalls_WithWrapIfNeededOption() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = false;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().RIGHT_MARGIN = 50;

    String before = "a.current.current.getThis().getThis().getThis().getThis();";

    doMethodTest(
      before,
      "a.current.current.getThis().getThis()\n" +
      "        .getThis().getThis();"
    );

    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;

    doMethodTest(
      before,
      "a.current.current.getThis().getThis()\n" +
      "                 .getThis().getThis();"
    );

    getSettings().RIGHT_MARGIN = 75;
    doMethodTest(before, before);
  }

  public void testAlignMethodCalls_PassedAsParameters_InMethodCall() {
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    doMethodTest(
      "test(call1(),\n" +
      "             call2(),\n" +
      "                        call3());\n",
      "test(call1(),\n" +
      "     call2(),\n" +
      "     call3());\n"
    );
  }

  public void testLocalVariablesAlignment() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "int a = 2;\n" +
      "String myString = \"my string\"",
      "int    a        = 2;\n" +
      "String myString = \"my string\""
    );
  }

  public void testAlignOnlyDeclarationStatements() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "      String s;\n" +
      "   int a = 2;\n" +
      "s = \"abs\";\n" +
      "long stamp = 12;",
      "String s;\n" +
      "int    a = 2;\n" +
      "s = \"abs\";\n" +
      "long stamp = 12;"
    );
  }

  public void testDoNotAlignWhenBlankLine() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "int a = 2;\n" +
      "\n" +
      "String myString = \"my string\"",
      "int a = 2;\n" +
      "\n" +
      "String myString = \"my string\""
    );
  }

  public void testDoNotAlignWhenGroupInterrupted() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "int a = 2;\n" +
      "System.out.println(\"hi!\")\n" +
      "String myString = \"my string\"",
      "int a = 2;\n" +
      "System.out.println(\"hi!\")\n" +
      "String myString = \"my string\""
    );
  }

  public void testDoNotAlignMultiDeclarations() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "  int a, b = 2;\n" +
      "String myString = \"my string\"",
      "int    a, b     = 2;\n" +
      "String myString = \"my string\""
    );
  }

  public void testDoNotAlignMultilineParams() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;

    doMethodTest(
      "int a = 12;\n" +
      "  Runnable runnable = new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "        System.out.println(\"AAA!\");\n" +
      "    }\n" +
      "};",

      "int a = 12;\n" +
      "Runnable runnable = new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "        System.out.println(\"AAA!\");\n" +
      "    }\n" +
      "};"
    );

    doMethodTest(
      "   Runnable runnable = new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "        System.out.println(\"AAA!\");\n" +
      "    }\n" +
      "};\n" +
      "int c = 12;",

      "Runnable runnable = new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "        System.out.println(\"AAA!\");\n" +
      "    }\n" +
      "};\n" +
      "int c = 12;"
    );

    doMethodTest(
      "    int ac = 99;\n" +
      "Runnable runnable = new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "        System.out.println(\"AAA!\");\n" +
      "    }\n" +
      "};\n" +
      "int c = 12;",

      "int ac = 99;\n" +
      "Runnable runnable = new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "        System.out.println(\"AAA!\");\n" +
      "    }\n" +
      "};\n" +
      "int c = 12;"
    );
  }

  public void testDoNotAlign_IfFirstMultiline() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;

    doMethodTest(
      "int\n" +
      "       i = 0;\n" +
      "int[] a = new int[]{1, 2, 0x0052, 0x0053, 0x0054};\n" +
      "int var1 = 1;\n" +
      "int var2 = 2;",

      "int\n" +
      "        i = 0;\n" +
      "int[] a    = new int[]{1, 2, 0x0052, 0x0053, 0x0054};\n" +
      "int   var1 = 1;\n" +
      "int   var2 = 2;"
    );
  }

  public void testAlign_InMethod() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doClassTest(
      "public void run() {\n" +
      "\n" +
      "                int a = 2;\n" +
      "            String superString = \"\";\n" +
      "\n" +
      "     test(call1(), call2(), call3());\n" +
      "    }",

      "public void run() {\n" +
      "\n" +
      "    int    a           = 2;\n" +
      "    String superString = \"\";\n" +
      "\n" +
      "    test(call1(), call2(), call3());\n" +
      "}"
    );

    doClassTest(
      "public void run() {\n" +
      "\n" +
      "        test(call1(), call2(), call3());\n" +
      "\n" +
      "        int a = 2;\n" +
      "             String superString = \"\";\n" +
      "}",
      "public void run() {\n" +
      "\n" +
      "    test(call1(), call2(), call3());\n" +
      "\n" +
      "    int    a           = 2;\n" +
      "    String superString = \"\";\n" +
      "}");
  }

  public void test_Shift_All_AlignedParameters() {
    myLineRange = new TextRange(2, 2);
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTextTest(
      REFORMAT_WITH_CONTEXT,
      "public class Test {\n" +
      "  \n" +
      "    public void fooooo(String foo,\n" +
      "                    String booo,\n" +
      "                    String kakadoo) {\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "}",

      "public class Test {\n" +
      "\n" +
      "    public void fooooo(String foo,\n" +
      "                       String booo,\n" +
      "                       String kakadoo) {\n" +
      "\n" +
      "    }\n" +
      "\n" +
      "}"
    );
  }

  public void test_Align_UnselectedField_IfNeeded() {
    myLineRange = new TextRange(2, 2);
    getSettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTextTest(
      REFORMAT_WITH_CONTEXT,
      "public class Test {\n" +
      "    public int    i = 1;\n" +
      "    public String iiiiiiiiii = 2;\n" +
      "}",
      "public class Test {\n" +
      "    public int    i          = 1;\n" +
      "    public String iiiiiiiiii = 2;\n" +
      "}"
    );
  }

  public void test_Align_UnselectedVariable_IfNeeded() {
    myLineRange = new TextRange(3, 3);
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doTextTest(
      REFORMAT_WITH_CONTEXT,
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int s = 2;\n" +
      "        String sssss = 3;\n" +
      "    }\n" +
      "}",
      "public class Test {\n" +
      "    public void test() {\n" +
      "        int    s     = 2;\n" +
      "        String sssss = 3;\n" +
      "    }\n" +
      "}"
    );
  }

  public void test_Align_ConsecutiveVars_InsideIfBlock() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "if (a > 2) {\n" +
      "int a=2;\n" +
      "String name=\"Yarik\";\n" +
      "}\n",
      "if (a > 2) {\n" +
      "    int    a    = 2;\n" +
      "    String name = \"Yarik\";\n" +
      "}\n"
    );
  }

  public void test_Align_ConsecutiveVars_InsideForBlock() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "    for (int i = 0; i < 10; i++) {\n" +
      "      int a=2;\n" +
      "      String name=\"Xa\";\n" +
      "    }\n",
      "for (int i = 0; i < 10; i++) {\n" +
      "    int    a    = 2;\n" +
      "    String name = \"Xa\";\n" +
      "}\n"
    );
  }

  public void test_Align_ConsecutiveVars_InsideTryBlock() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "    try {\n" +
      "      int x = getX();\n" +
      "      String name = \"Ha\";\n" +
      "    }\n" +
      "    catch (IOException exception) {\n" +
      "      int y = 12;\n" +
      "      String test = \"Test\";\n" +
      "    }\n" +
      "    finally {\n" +
      "      int z = 12;\n" +
      "      String zzzz = \"pnmhd\";\n" +
      "    }\n",
      "try {\n" +
      "    int    x    = getX();\n" +
      "    String name = \"Ha\";\n" +
      "} catch (IOException exception) {\n" +
      "    int    y    = 12;\n" +
      "    String test = \"Test\";\n" +
      "} finally {\n" +
      "    int    z    = 12;\n" +
      "    String zzzz = \"pnmhd\";\n" +
      "}\n"
    );
  }

  public void test_Align_ConsecutiveVars_InsideCodeBlock() {
    getSettings().ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = true;
    doMethodTest(
      "    System.out.println(\"AAAA\");\n" +
      "    int a = 2;\n" +
      "    \n" +
      "    {\n" +
      "      int x=2;\n" +
      "      String name=3;\n" +
      "    }\n",
      "System.out.println(\"AAAA\");\n" +
      "int a = 2;\n" +
      "\n" +
      "{\n" +
      "    int    x    = 2;\n" +
      "    String name = 3;\n" +
      "}\n"
    );
  }
  
  public void test_AlignComments_BetweenChainedMethodCalls() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doMethodTest(
      "ActionBarPullToRefresh.from(getActivity())\n" +
      "        // Mark the ListView as pullable\n" +
      "        .theseChildrenArePullable(eventsListView)\n" +
      "                // Set the OnRefreshListener\n" +
      "        .listener(this)\n" +
      "                // Use the AbsListView delegate for StickyListHeadersListView\n" +
      "        .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())\n" +
      "                // Finally commit the setup to our PullToRefreshLayout\n" +
      "        .setup(mPullToRefreshLayout);",
      "ActionBarPullToRefresh.from(getActivity())\n" +
      "                      // Mark the ListView as pullable\n" +
      "                      .theseChildrenArePullable(eventsListView)\n" +
      "                      // Set the OnRefreshListener\n" +
      "                      .listener(this)\n" +
      "                      // Use the AbsListView delegate for StickyListHeadersListView\n" +
      "                      .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())\n" +
      "                      // Finally commit the setup to our PullToRefreshLayout\n" +
      "                      .setup(mPullToRefreshLayout);"
    );
  }
  
  public void test_AlignComments_2() {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doClassTest(
      "public String returnWithBuilder2() {\n" +
      "    return MoreObjects\n" +
      "        .toStringHelper(this)\n" +
      "        .add(\"value\", value)\n" +
      "                   // comment\n" +
      "        .toString();\n" +
      "  }",
      "public String returnWithBuilder2() {\n" +
      "    return MoreObjects\n" +
      "            .toStringHelper(this)\n" +
      "            .add(\"value\", value)\n" +
      "            // comment\n" +
      "            .toString();\n" +
      "}"
    );
  }
  
  public void test_AlignSubsequentOneLineMethods() {
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().ALIGN_SUBSEQUENT_SIMPLE_METHODS = true;
    doTextTest(
      "public class Test {\n" +
      "\n" +
      "    public void testSuperDuperFuckerMother() { System.out.println(\"AAA\"); }\n" +
      "\n" +
      "    public void testCounterMounter() { System.out.println(\"XXXX\"); }\n" +
      "\n" +
      "}",
      "public class Test {\n" +
      "\n" +
      "    public void testSuperDuperFuckerMother() { System.out.println(\"AAA\"); }\n" +
      "\n" +
      "    public void testCounterMounter()         { System.out.println(\"XXXX\"); }\n" +
      "\n" +
      "}"
    );
  }
}
