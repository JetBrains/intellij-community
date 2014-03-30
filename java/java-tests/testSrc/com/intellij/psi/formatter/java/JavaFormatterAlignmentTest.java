/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;

/**
 * Is intended to hold specific java formatting tests for alignment settings (
 * <code>Project Settings - Code Style - Alignment and Braces</code>).
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:42:00 PM
 */
public class JavaFormatterAlignmentTest extends AbstractJavaFormatterTest {

  public void testChainedMethodsAlignment() throws Exception {
    // Inspired by IDEA-30369
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 8;
    doTest();
  }

  public void testMethodAndChainedField() throws Exception {
    // Inspired by IDEA-79806

    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doMethodTest(
      "Holder.INSTANCE\n" +
      "                .foo();",
      "Holder.INSTANCE\n" +
      "        .foo();"
    );
  }

  public void testMultipleMethodAnnotationsCommentedInTheMiddle() throws Exception {
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

  public void testTernaryOperator() throws Exception {
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

  public void testMethodBrackets() throws Exception {
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

  public void testTabsAndFieldsInColumnsAlignment() throws Exception {
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
  
  public void testAlignThrowsKeyword() throws Exception {
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

  public void testAlignResourceList() throws Exception {
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


  public void testChainedMethodCallsAfterFieldsChain_WithAlignment() throws Exception {
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

  public void testChainedMethodCallsAfterFieldsChain_WithoutAlignment() throws Exception {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = false;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doMethodTest(
      "a.current.current.current.getThis().getThis().getThis();",

      "a.current.current.current.getThis()\n" +
      "        .getThis()\n" +
      "        .getThis();"
    );
  }

  public void testChainedMethodCalls_WithChopDownIfLongOption() throws Exception {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM; // it's equal to "Chop down if long"
    getSettings().getRootSettings().RIGHT_MARGIN = 50;

    String before = "a.current.current.getThis().getThis().getThis().getThis().getThis();";
    doMethodTest(
      before,
      "a.current.current.getThis()\n" +
      "                 .getThis()\n" +
      "                 .getThis()\n" +
      "                 .getThis()\n" +
      "                 .getThis();"
    );

    getSettings().getRootSettings().RIGHT_MARGIN = 80;
    doMethodTest(before, before);
  }

  public void testChainedMethodCalls_WithWrapIfNeededOption() throws Exception {
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = false;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().getRootSettings().RIGHT_MARGIN = 50;

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

    getSettings().getRootSettings().RIGHT_MARGIN = 75;
    doMethodTest(before, before);
  }
}
