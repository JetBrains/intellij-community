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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;


/**
 * <b>Note:</b> this class is too huge and hard to use. It's tests are intended to be split in multiple more fine-grained
 * java formatting test classes.
 */
@SuppressWarnings({"Deprecation"})
public class JavaFormatterTest extends AbstractJavaFormatterTest {

  public void testForEach() {
    doTest("ForEach.java", "ForEach_after.java");
  }

  public void testDoubleCast() {
    doTest("DoubleCast.java", "DoubleCast_after.java");
  }

  public void test1() {
    myTextRange = new TextRange(35, 46);
    doTest("1.java", "1_after.java");
  }

  public void testLabel1() {
    CommonCodeStyleSettings settings = getSettings();

    settings.LABELED_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).LABEL_INDENT_ABSOLUTE = true;
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).LABEL_INDENT_SIZE = 0;

    doTest("Label.java", "Label_after1.java");
  }

  public void testTryCatch() {
    myTextRange = new TextRange(38, 72);
    doTest("TryCatch.java", "TryCatch_after.java");
  }

  public void testNullMethodParameter() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest("NullMethodParameter.java", "NullMethodParameter_after.java");
  }
  
  public void test_DoNot_JoinLines_If_KeepLineBreaksIsOn() {
    getSettings().KEEP_LINE_BREAKS = true;
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTextTest(
      "public class Test<Param> {\n" +
      "    @SuppressWarnings(\"unchecked\")\n" +
      "        void executeParallel(Param... params) {\n" +
      "    }\n" +
      "}",
      "public class Test<Param> {\n" +
      "    @SuppressWarnings(\"unchecked\")\n" +
      "    void executeParallel(Param... params) {\n" +
      "    }\n" +
      "}"
    );
  }
  
  public void test_DoNot_JoinLines_If_KeepLineBreaksIsOn_WithMultipleAnnotations() {
    getSettings().KEEP_LINE_BREAKS = true;
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTextTest(
      "public class Test<Param> {\n" +
      "    @Override @SuppressWarnings(\"unchecked\")\n" +
      "        void executeParallel(Param... params) {\n" +
      "    }\n" +
      "}",
      "public class Test<Param> {\n" +
      "    @Override @SuppressWarnings(\"unchecked\")\n" +
      "    void executeParallel(Param... params) {\n" +
      "    }\n" +
      "}"
    );
  }
  
  public void test_format_only_selected_range() {
    myTextRange = new TextRange(18, 19);
    doTextTest(
      "public class X {\n" +
      " public int a =       2;\n" +
      "}",
      "public class X {\n" +
      "    public int a =       2;\n" +
      "}"
    );
  }

  public void testNew() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 8;
    doTest("New.java", "New_after.java");
  }

  public void testJavaDoc() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.BLANK_LINES_AROUND_FIELD = 1;
    doTest("JavaDoc.java", "JavaDoc_after.java");
  }

  public void testBreakInsideIf() {
    doTest("BreakInsideIf.java", "BreakInsideIf_after.java");
  }

  public void testAssert() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.HIGHEST);
    doTest();
  }

  public void testCastInsideElse() {
    final CommonCodeStyleSettings settings = getSettings();
    final CommonCodeStyleSettings.IndentOptions indentOptions = settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA);
    indentOptions.CONTINUATION_INDENT_SIZE = 2;
    indentOptions.INDENT_SIZE = 2;
    indentOptions.LABEL_INDENT_SIZE = 0;
    indentOptions.TAB_SIZE = 8;
    settings.SPACE_WITHIN_CAST_PARENTHESES = false;
    settings.SPACE_AFTER_TYPE_CAST = true;
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;
    doTest();
  }

  public void testAlignMultiLine() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
  }

  public void testInnerClassAsParameter() {
    doTest();
  }

  public void testSynchronizedBlock() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false;
    settings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = false;
    settings.SPACE_BEFORE_SYNCHRONIZED_LBRACE = false;
    doTest();
  }

  public void testMethodCallInAssignment() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 8;
    doTest();
  }

  public void testAnonymousInnerClasses() {
    doTest();
  }

  public void testAnonymousInner2() {
    doTest();
  }

  public void testWrapAssertion() {
    doTest();
  }
  
  public void testIfElse() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.DO_NOT_FORCE;
    settings.FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    settings.WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    settings.DOWHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;

    settings.ELSE_ON_NEW_LINE = true;
    settings.SPECIAL_ELSE_IF_TREATMENT = false;
    settings.WHILE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = true;
    settings.FINALLY_ON_NEW_LINE = true;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;
    settings.ALIGN_MULTILINE_ASSIGNMENT = true;
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;
    settings.ALIGN_MULTILINE_THROWS_LIST = true;
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true;
    settings.ALIGN_MULTILINE_FOR = true;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.ALIGN_MULTILINE_PARAMETERS = true;
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    settings.WHILE_ON_NEW_LINE = true;
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doTest();
  }

  public void testIfBraces() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    settings.KEEP_LINE_BREAKS = false;
    doTest();
  }

  public void testTernaryExpression() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTest();

    settings.ALIGN_MULTILINE_TERNARY_OPERATION = false;
    doTest("TernaryExpression.java", "TernaryExpression_DoNotAlign_after.java");

  }

  public void testAlignAssignment() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ALIGN_MULTILINE_ASSIGNMENT = true;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
  }

  public void testAlignFor() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    settings.ALIGN_MULTILINE_FOR = true;
    doTest();
  }

  public void testSwitch() {
    doTest();
  }

  public void testContinue() {
    doTest();
  }

  public void testIf() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doTest("If.java", "If.java");
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    settings.KEEP_LINE_BREAKS = false;
    doTest("If_after.java", "If.java");

  }

  public void test2() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void testBlocks() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.KEEP_LINE_BREAKS = false;
    doTest();
  }

  public void testBinaryOperation() throws IncorrectOperationException {
    final CommonCodeStyleSettings settings = getSettings();

    @NonNls String text = "class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        + ccc + eee + ddd;\n" + "    }\n" + "}";


    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    settings.ALIGN_MULTILINE_ASSIGNMENT = true;
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "              + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}");

    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    settings.ALIGN_MULTILINE_ASSIGNMENT = false;
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "              + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}");


    settings.ALIGN_MULTILINE_BINARY_OPERATION = false;
    settings.ALIGN_MULTILINE_ASSIGNMENT = true;
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}");


    settings.ALIGN_MULTILINE_ASSIGNMENT = false;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = false;
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}");

    settings.ALIGN_MULTILINE_ASSIGNMENT = false;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = false;

    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}");


    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest("class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        - ccc + eee + ddd;\n" + "    }\n" + "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        xxx = aaa + bbb\n" +
               "              - ccc + eee + ddd;\n" +
               "    }\n" +
               "}");

    doTextTest("class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        * ccc + eee + ddd;\n" + "    }\n" + "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        xxx = aaa + bbb\n" +
               "                    * ccc + eee + ddd;\n" +
               "    }\n" +
               "}");

  }

  public void testWhile() {
    doTextTest("class A{\n" + "void a(){\n" + "do x++ while (b);\n" + "}\n}",
               "class A {\n" + "    void a() {\n" + "        do x++ while (b);\n" + "    }\n" + "}");
  }

  public void testFor() {
    doTextTest("class A{\n" + "void b(){\n" + "for (c) {\n" + "d();\n" + "}\n" + "}\n" + "}",
               "class A {\n" + "    void b() {\n" + "        for (c) {\n" + "            d();\n" + "        }\n" + "    }\n" + "}");
  }

  public void testClassComment() {
    String before = "/**\n" +
                  "* @author smbd\n" +
                  "* @param <T> some param\n" +
                  "* @since 1.9\n" +
                  "*/\n" +
                  "class Test<T>{}";
    String after = "/**\n" +
                   " * @param <T> some param\n" +
                   " * @author smbd\n" +
                   " * @since 1.9\n" +
                   " */\n" +
                   "class Test<T> {\n" +
                   "}";
    doTextTest(before,after);
  }

  public void testStringBinaryOperation() {
    final CommonCodeStyleSettings settings = getSettings();

    settings.ALIGN_MULTILINE_ASSIGNMENT = false;
    settings.ALIGN_MULTILINE_BINARY_OPERATION = false;

    doTextTest("class Foo {\n" + "    void foo () {\n" + "String s = \"abc\" +\n" + "\"def\";" + "    }\n" + "}",

               "class Foo {\n" +
               "    void foo() {\n" +
               "        String s = \"abc\" +\n" +
               "                \"def\";\n" +
               "    }\n" +
               "}");

  }

  public void test3() {
    doTest();
  }

  public void test4() {
    myLineRange = new TextRange(2, 8);
    doTest();
  }

  public void testBraces() {
    final CommonCodeStyleSettings settings = getSettings();

    @NonNls final String text =
      "class Foo {\n" +
      "void foo () {\n" +
      "if (a) {\n" +
      "int i = 0;\n" +
      "}\n" +
      "}\n" +
      "}";

    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        if (a) {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "    }\n" +
                     "}");

    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "    {\n" +
                     "        if (a)\n" +
                     "        {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "    }\n" +
                     "}");


    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "        if (a)\n" +
                     "            {\n" +
                     "            int i = 0;\n" +
                     "            }\n" +
                     "        }\n" +
                     "}");

    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "        if (a) {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "        }\n" +
                     "}");


    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
    doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "            if (a)\n" +
                     "                {\n" +
                     "                    int i = 0;\n" +
                     "                }\n" +
                     "        }\n" +
                     "}");

    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTextTest("class Foo {\n" + "    static{\n" + "foo();\n" + "}" + "}",
               "class Foo {\n" + "    static\n" + "    {\n" + "        foo();\n" + "    }\n" + "}");

  }

  public void testExtendsList() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;
    doTextTest("class A extends B, \n" + "C {}", "class A extends B,\n" + "                C {\n}");
  }

  public void testBlockWithoutBraces() {
    doTextTest("class A {\n" + "void foo(){\n" + "if(a)\n" + "return;\n" + "}\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        if (a)\n" + "            return;\n" + "    }\n" + "}");
  }

  public void testNestedCalls() {
    doTextTest("class A {\n" + "void foo(){\n" + "foo(\nfoo(\nfoo()\n)\n);\n" + "}\n" + "}", "class A {\n" +
                                                                                             "    void foo() {\n" +
                                                                                             "        foo(\n" +
                                                                                             "                foo(\n" +
                                                                                             "                        foo()\n" +
                                                                                             "                )\n" +
                                                                                             "        );\n" +
                                                                                             "    }\n" +
                                                                                             "}");

  }

  public void testSpacesAroundMethod() {
    doTextTest("class Foo {\n" + "    abstract void a();\n" + "    {\n" + "        a();\n" + "    }\n" + "}",
               "class Foo {\n" + "    abstract void a();\n" + "\n" + "    {\n" + "        a();\n" + "    }\n" + "}");
  }

  public void testSpaceInIf() {
    doTextTest("class foo {\n" +
               "    {\n" +
               "        if (a) {\n" +
               "            if(a) {\n" +
               "\n" +
               "            }\n" +
               "        }\n" +
               "    }\n" +
"}", "class foo {\n" +
     "    {\n" +
     "        if (a) {\n" +
     "            if (a) {\n" +
     "\n" +
     "            }\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testIf2() {
    doTextTest(
      "public class Test {\n" + "    public boolean equals(Object o) {\n" + "        if(this == o)return true;\n" + "    }\n" + "}",
      "public class Test {\n" + "    public boolean equals(Object o) {\n" + "        if (this == o) return true;\n" + "    }\n" + "}");
  }

  public void testSpaceAroundField() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.BLANK_LINES_AROUND_FIELD = 1;

    doTextTest("class Foo {\n" +
               "    boolean a;\n" +
               "    {\n" +
               "        if (a) {\n" +
               "        } else {\n" +
               "\n" +
               "        }\n" +
               "        a = 2;\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    boolean a;\n" +
     "\n" +
     "    {\n" +
     "        if (a) {\n" +
     "        } else {\n" +
     "\n" +
     "        }\n" +
     "        a = 2;\n" +
     "    }\n" +
     "}");
  }

  public void testArray() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = true;
    settings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true;
    doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{0};\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[] { 0 };\n" + "    }\n" + "}");
  }

  public void testEmptyArray() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = true;
    settings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true;
    settings.SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = false;
    doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{ };\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[] {};\n" + "    }\n" + "}");
  }

  public void testEmptyArrayIsntWrapped() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true;
    settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true;
    settings.SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = true;
    doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{ };\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[]{ };\n" + "    }\n" + "}");
  }

  public void testTwoJavaDocs() {
    doTextTest("/**\n" + " * \n" + " */\n" + "        class Test {\n" + "    /**\n" + "     */\n" + "     public void foo();\n" + "}",
               "/**\n" + " *\n" + " */\n" + "class Test {\n" + "    /**\n" + "     */\n" + "    public void foo();\n" + "}");
  }

  public void testJavaDocLinksWithParameterNames() {
    // See IDEADEV-8332
    doTextTest("/**\n" +
               "* @return if ( x1 == x1 ) then return {@link #cmp(String y1,int y2)}\n" +
               "*         otherwise return {@link #cmp(int x1,int x2)}\n" +
               "*/\n" +
               "class X {\n" +
               "}\n", "/**\n" +
                      " * @return if ( x1 == x1 ) then return {@link #cmp(String y1, int y2)}\n" +
                      " * otherwise return {@link #cmp(int x1, int x2)}\n" +
                      " */\n" +
                      "class X {\n" +
                      "}\n");
  }

  public void testIncompleteField() {
    doTextTest("public class Test {\n" + "    String s =;\n" + "}", "public class Test {\n" + "    String s = ;\n" + "}");
  }

  public void testIf3() {
    getSettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false;
    doTextTest("public abstract class A {\n" +
               "    abstract void f(boolean b);\n" +
               "\n" +
               "    A IMPL = new A() {\n" +
               "        void f(boolean b) {\n" +
               "            if (b)\n" +
               "                f(true); else {\n" +
               "                f(false);\n" +
               "                f(false);\n" +
               "            }\n" +
               "            for (int i = 0; i < 5; i++) f(true);\n" +
               "        }\n" +
               "    };\n" +
"}", "public abstract class A {\n" +
     "    abstract void f(boolean b);\n" +
     "\n" +
     "    A IMPL = new A() {\n" +
     "        void f(boolean b) {\n" +
     "            if (b)\n" +
     "                f(true);\n" +
     "            else {\n" +
     "                f(false);\n" +
     "                f(false);\n" +
     "            }\n" +
     "            for (int i = 0; i < 5; i++)\n" +
     "                f(true);\n" +
     "        }\n" +
     "    };\n" +
     "}");
  }

  public void testDocComment() {
    doTextTest("public class TestClass {\n" + "/**\n" + "* \n" + "*/\n" + "    public void f1() {\n" + "    }\n" + "}",
               "public class TestClass {\n" + "    /**\n" + "     *\n" + "     */\n" + "    public void f1() {\n" + "    }\n" + "}");
  }

  public void testDocComment2() {
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    doTextTest("class Test {\n" +
               "/**\n" +
               "*\n" +
               "* @param a\n" +
               "* @param param\n" +
               "* @param ddd\n" +
               "*/\n" +
               "    public void foo(int a, String param, double ddd) {}\n" +
"}", "class Test {\n" +
     "    /**\n" +
     "     * @param a\n" +
     "     * @param param\n" +
     "     * @param ddd\n" +
     "     */\n" +
     "    public void foo(int a, String param, double ddd) {}\n" +
     "}");
  }

  public void testSpaceBeforeFieldName() {
    doTextTest("class A{\n" + "public   A    myA ;\n" + "}", "class A {\n" + "    public A myA;\n" + "}");
  }

  public void testClass() {
    doTextTest("    class A {\n" +
               "        Logger LOG;\n" +
               "        class C {}\n" +
               "\n" +
               "        public void b() {\n" +
               "        }\n" +
               "\n" +
               "        int f;\n" +
"    }", "class A {\n" +
         "    Logger LOG;\n" +
         "\n" +
         "    class C {\n" +
         "    }\n" +
         "\n" +
         "    public void b() {\n" +
         "    }\n" +
         "\n" +
         "    int f;\n" +
         "}");
  }

  public void testDoNotIndentCaseFromSwitch() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.INDENT_CASE_FROM_SWITCH = false;
    doTextTest("class A {\n" + "void foo() {\n" + "switch(a){\n" + "case 1: \n" + "break;\n" + "}\n" + "}\n" + "}", "class A {\n" +
                                                                                                                    "    void foo() {\n" +
                                                                                                                    "        switch (a) {\n" +
                                                                                                                    "        case 1:\n" +
                                                                                                                    "            break;\n" +
                                                                                                                    "        }\n" +
                                                                                                                    "    }\n" +
                                                                                                                    "}");
  }

  public void testClass2() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.KEEP_FIRST_COLUMN_COMMENT = false;
    doTextTest("class A {\n" + "// comment before\n" + "protected Object a;//  comment after\n" + "}",
               "class A {\n" + "    // comment before\n" + "    protected Object a;//  comment after\n" + "}");
  }

  public void testSplitLiteral() {
    doTextTest("class A {\n" + "void foo() {\n" + "  String s = \"abc\" +\n" + "  \"def\";\n" + "}\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        String s = \"abc\" +\n" + "                \"def\";\n" + "    }\n" + "}");
  }

  public void testParametersAlignment() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    settings.RIGHT_MARGIN = 140;
    doTest();
  }

  public void testConditionalExpression() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.SPACE_BEFORE_QUEST = true;
    settings.SPACE_AFTER_QUEST = false;
    settings.SPACE_BEFORE_COLON = true;
    settings.SPACE_AFTER_COLON = false;

    doTextTest("class Foo{\n" + "  void foo(){\n" + "  return name   !=   null   ?   1   :   2   ;" + "}\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        return name != null ?1 :2;\n" + "    }\n" + "}");
  }

  public void testMethodCallChain() {
    doTextTest("class Foo{\n" +
               "    void foo(){\n" +
               "       configuration = new Configuration() \n" +
               "                .setProperty(\"hibernate.dialect\", \n" +
               "                \"au.com.sensis.wsearch.db.CustomHSQLDBDialect\");\n" +
               "}\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        configuration = new Configuration()\n" +
     "                .setProperty(\"hibernate.dialect\",\n" +
     "                        \"au.com.sensis.wsearch.db.CustomHSQLDBDialect\");\n" +
     "    }\n" +
     "}");

    doTextTest("class Foo{\n" +
               "    void foo(){\n" +
               "       configuration = new Configuration(). \n" +
               "                setProperty(\"hibernate.dialect\", \n" +
               "                \"au.com.sensis.wsearch.db.CustomHSQLDBDialect\");\n" +
               "}\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        configuration = new Configuration().\n" +
     "                setProperty(\"hibernate.dialect\",\n" +
     "                        \"au.com.sensis.wsearch.db.CustomHSQLDBDialect\");\n" +
     "    }\n" +
     "}");

    doTextTest("class Foo{\n" +
               "    void foo(){\n" +
               "       configuration = new Configuration() \n" +
               "                .setProperty(\"hibernate.dialect\", \n" +
               "                \"au.com.sensis.wsearch.db.CustomHSQLDBDialect\") \n" +
               "                .setProperty(\"hibernate.connection.url\", \n" +
               "                \"jdbc:hsqldb:mem:testdb\") \n" +
               "                .setProperty(\"hibernate.connection.driver_class\", \n" +
               "                \"org.hsqldb.jdbcDriver\") \n" +
               "                .setProperty(\"hibernate.connection.username\", \"sa\") \n" +
               "                .setProperty(\"hibernate.connection.password\", \"\") \n" +
               "                .setProperty(\"hibernate.show_sql\", \"false\") \n" +
               "                .setProperty(\"hibernate.order_updates\", \"true\") \n" +
               "                .setProperty(\"hibernate.hbm2ddl.auto\", \"update\"); " +
               "}\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        configuration = new Configuration()\n" +
     "                .setProperty(\"hibernate.dialect\",\n" +
     "                        \"au.com.sensis.wsearch.db.CustomHSQLDBDialect\")\n" +
     "                .setProperty(\"hibernate.connection.url\",\n" +
     "                        \"jdbc:hsqldb:mem:testdb\")\n" +
     "                .setProperty(\"hibernate.connection.driver_class\",\n" +
     "                        \"org.hsqldb.jdbcDriver\")\n" +
     "                .setProperty(\"hibernate.connection.username\", \"sa\")\n" +
     "                .setProperty(\"hibernate.connection.password\", \"\")\n" +
     "                .setProperty(\"hibernate.show_sql\", \"false\")\n" +
     "                .setProperty(\"hibernate.order_updates\", \"true\")\n" +
     "                .setProperty(\"hibernate.hbm2ddl.auto\", \"update\");\n" +
     "    }\n" +
     "}");
  }

  public void testComment1() {
    doTextTest(
      "class Foo {\n" +
      "    public boolean mErrorFlage;\n" +
      "\n" +
      "    /**\n" +
      "     * Reference to New Member Message Source\n" +
      "     */\n" +
      "    private NewMemberMessageSource newMemberMessageSource;" +
      "\n" +
      "}",

      "class Foo {\n" +
      "    public boolean mErrorFlage;\n" +
      "\n" +
      "    /**\n" +
      "     * Reference to New Member Message Source\n" +
      "     */\n" +
      "    private NewMemberMessageSource newMemberMessageSource;" +
      "\n" +
      "}");
  }

  public void testElseAfterComment() {
    doTextTest("public class Foo {\n" +
               "    public int foo() {\n" +
               "        if (a) {\n" +
               "            return;\n" +
               "        }//comment\n" +
               "        else {\n" +
               "        }\n" +
               "    }\n" +
"}", "public class Foo {\n" +
     "    public int foo() {\n" +
     "        if (a) {\n" +
     "            return;\n" +
     "        }//comment\n" +
     "        else {\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testLBraceAfterComment() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.KEEP_LINE_BREAKS = false;
    doTextTest("public class Foo {\n" +
               "    public int foo() {\n" +
               "        if (a) \n" +
               "  //comment\n" +
               "{\n" +
               "            return;\n" +
               "        }\n" +
               "    }\n" +
"}", "public class Foo {\n" +
     "    public int foo() {\n" +
     "        if (a)\n" +
     "        //comment\n" +
     "        {\n" +
     "            return;\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testSpaces() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.SPACE_WITHIN_FOR_PARENTHESES = true;
    settings.SPACE_WITHIN_IF_PARENTHESES = true;
    settings.SPACE_WITHIN_METHOD_PARENTHESES = true;
    settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    settings.SPACE_BEFORE_METHOD_PARENTHESES = true;
    settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    doTest();
  }

  public void testSpacesBeforeLBrace() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.SPACE_BEFORE_CLASS_LBRACE = true;
    settings.SPACE_BEFORE_METHOD_LBRACE = true;
    settings.SPACE_BEFORE_IF_LBRACE = true;
    settings.SPACE_BEFORE_ELSE_LBRACE = true;
    settings.SPACE_BEFORE_WHILE_LBRACE = true;
    settings.SPACE_BEFORE_FOR_LBRACE = true;
    settings.SPACE_BEFORE_DO_LBRACE = true;
    settings.SPACE_BEFORE_SWITCH_LBRACE = true;
    settings.SPACE_BEFORE_TRY_LBRACE = true;
    settings.SPACE_BEFORE_CATCH_LBRACE = true;
    settings.SPACE_BEFORE_FINALLY_LBRACE = true;
    settings.SPACE_BEFORE_SYNCHRONIZED_LBRACE = true;
    settings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true;

    doTest();

    settings.SPACE_BEFORE_CLASS_LBRACE = false;
    settings.SPACE_BEFORE_METHOD_LBRACE = false;
    settings.SPACE_BEFORE_IF_LBRACE = false;
    settings.SPACE_BEFORE_ELSE_LBRACE = false;
    settings.SPACE_BEFORE_WHILE_LBRACE = false;
    settings.SPACE_BEFORE_FOR_LBRACE = false;
    settings.SPACE_BEFORE_DO_LBRACE = false;
    settings.SPACE_BEFORE_SWITCH_LBRACE = false;
    settings.SPACE_BEFORE_TRY_LBRACE = false;
    settings.SPACE_BEFORE_CATCH_LBRACE = false;
    settings.SPACE_BEFORE_FINALLY_LBRACE = false;
    settings.SPACE_BEFORE_SYNCHRONIZED_LBRACE = false;
    settings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;

    doTest("SpacesBeforeLBrace.java", "SpacesBeforeLBrace.java");
  }

  public void testCommentBeforeField() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.KEEP_LINE_BREAKS = false;
    settings.KEEP_FIRST_COLUMN_COMMENT = false;
    settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false;
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;
    doTextTest("class Foo{\n" + "    //Foo a\n" + "    Foo a; \n" + "}", "class Foo {\n" + "    //Foo a\n" + "    Foo a;\n" + "}");
  }

  public void testLabel() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).LABEL_INDENT_ABSOLUTE = true;
    settings.SPECIAL_ELSE_IF_TREATMENT = true;
    settings.FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    myTextRange = new TextRange(59, 121);
    doTextTest("public class Foo {\n" +
               "    public void foo() {\n" +
               "label2:\n" +
               "        for (int i = 0; i < 5; i++)\n" +
               "        {doSomething(i);\n" +
               "        }\n" +
               "    }\n" +
"}", "public class Foo {\n" +
     "    public void foo() {\n" +
     "label2:\n" +
     "        for (int i = 0; i < 5; i++) {\n" +
     "            doSomething(i);\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testElseOnNewLine() {
    doTextTest("class Foo{\n" + "void foo() {\n" + "if (a)\n" + "return;\n" + "else\n" + "return;\n" + "}\n" + "}", "class Foo {\n" +
                                                                                                                    "    void foo() {\n" +
                                                                                                                    "        if (a)\n" +
                                                                                                                    "            return;\n" +
                                                                                                                    "        else\n" +
                                                                                                                    "            return;\n" +
                                                                                                                    "    }\n" +
                                                                                                                    "}");
  }

  public void testTwoClasses() {
    doTextTest("class A {}\n" + "class B {}", "class A {\n" + "}\n" + "\n" + "class B {\n" + "}");
  }

  public void testBraceOnNewLineIfWrapped() {
    getSettings().BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    getSettings().RIGHT_MARGIN = 35;
    getSettings().ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest("class Foo {\n" +
               "    void foo(){\n" +
               "        if (veryLongCondition || veryVeryVeryVeryLongCondition) {\n" +
               "            foo();\n" +
               "        }\n" +
               "        if (a) {\n" +
               "        }" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        if (veryLongCondition ||\n" +
     "            veryVeryVeryVeryLongCondition)\n" +
     "        {\n" +
     "            foo();\n" +
     "        }\n" +
     "        if (a) {\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testFirstArgumentWrapping() {
    getSettings().RIGHT_MARGIN = 20;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(1);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(\n" + "                1);\n" + "    }\n" + "}");

    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(1,2);" + "    }\n" + "}", "class Foo {\n" +
                                                                                                        "    void foo() {\n" +
                                                                                                        "        fooFooFooFoo(\n" +
                                                                                                        "                1,\n" +
                                                                                                        "                2);\n" +
                                                                                                        "    }\n" +
                                                                                                        "}");

    doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFoo(1,2);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        fooFooFoo(1,\n" + "                2);\n" + "    }\n" + "}");

  }

  public void testSpacesInsideWhile() {
    getSettings().SPACE_WITHIN_WHILE_PARENTHESES = true;
    doTextTest("class Foo{\n" + "    void foo() {\n" + "        while(x != y) {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        while ( x != y ) {\n" + "        }\n" + "    }\n" + "}");
  }

  public void testAssertStatementWrapping() {
    getSettings().ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings().RIGHT_MARGIN = 40;
    final JavaPsiFacade facade = getJavaFacade();
    final LanguageLevel effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    try {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);

      getSettings().ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false;
      doTextTest("class Foo {\n" +
                 "    void foo() {\n" +
                 "        assert methodWithVeryVeryLongName() : foo;\n" +
                 "        assert i + j + k + l + n + m <= 2 : \"assert description\";\n" +
                 "    }\n" +
"}\n", "class Foo {\n" +
       "    void foo() {\n" +
       "        assert methodWithVeryVeryLongName() :\n" +
       "                foo;\n" +
       "        assert i + j + k + l + n + m <= 2 :\n" +
       "                \"assert description\";\n" +
       "    }\n" +
       "}\n");

      getSettings().ASSERT_STATEMENT_COLON_ON_NEXT_LINE = true;
      doTextTest("class Foo {\n" +
                 "    void foo() {\n" +
                 "        assert methodWithVeryVeryLongName() : foo;\n" +
                 "        assert i + j + k + l + n + m <= 2 : \"assert description\";\n" +
                 "    }\n" +
"}\n", "class Foo {\n" +
       "    void foo() {\n" +
       "        assert methodWithVeryVeryLongName()\n" +
       "                : foo;\n" +
       "        assert i + j + k + l + n + m <= 2\n" +
       "                : \"assert description\";\n" +
       "    }\n" +
       "}\n");

    }
    finally {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(effectiveLanguageLevel);
    }
  }

  public void testAssertStatementWrapping2() {
    getSettings().BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings().ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().RIGHT_MARGIN = 37;

    final CommonCodeStyleSettings.IndentOptions options = getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA);
    options.INDENT_SIZE = 2;
    options.CONTINUATION_INDENT_SIZE = 2;

    getSettings().ASSERT_STATEMENT_COLON_ON_NEXT_LINE = true;

    final JavaPsiFacade facade = getJavaFacade();
    final LanguageLevel effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);

    try {
      doTextTest(
        "class Foo {\n" + "    void foo() {\n" + "        assert i + j + k + l + n + m <= 2 : \"assert description\";" + "    }\n" + "}",
        "class Foo {\n" +
        "  void foo() {\n" +
        "    assert i + j + k + l + n + m <= 2\n" +
        "      : \"assert description\";\n" +
        "  }\n" +
        "}");

      getSettings().ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false;

      doTextTest(
        "class Foo {\n" + "    void foo() {\n" + "        assert i + j + k + l + n + m <= 2 : \"assert description\";" + "    }\n" + "}",
        "class Foo {\n" +
        "  void foo() {\n" +
        "    assert\n" +
        "      i + j + k + l + n + m <= 2 :\n" +
        "      \"assert description\";\n" +
        "  }\n" +
        "}");
    }
    finally {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(effectiveLanguageLevel);
    }

  }

  public void test() {
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 2;
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 2;
    getSettings().RIGHT_MARGIN = 37;
    getSettings().ALIGN_MULTILINE_EXTENDS_LIST = true;

    getSettings().EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    getSettings().ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false;
    getSettings().ALIGN_MULTILINE_BINARY_OPERATION = true;

    final JavaPsiFacade facade = getJavaFacade();
    final LanguageLevel effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);


    try {
      doTextTest("public class ThisIsASampleClass extends C1 implements I1, I2, I3, I4, I5 {\n" +
                 "  public void longerMethod() {\n" +
                 "    assert i + j + k + l + n+ m <= 2 : \"assert description\";" +
                 "  }\n" +
"}", "public class ThisIsASampleClass\n" +
     "  extends C1\n" +
     "  implements I1, I2, I3, I4, I5 {\n" +
     "  public void longerMethod() {\n" +
     "    assert\n" +
     "      i + j + k + l + n + m <= 2 :\n" +
     "      \"assert description\";\n" +
     "  }\n" +
     "}");
    }
    finally {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(effectiveLanguageLevel);
    }
  }

  public void testLBrace() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getSettings().RIGHT_MARGIN = 14;
    doTextTest("class Foo {\n" + "    void foo() {\n" + "        \n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "\n" + "    }\n" + "}");
  }

  public void testJavaDocLeadingAsterisksAreDisabled() {
    getJavaSettings().JD_LEADING_ASTERISKS_ARE_ENABLED = false;
    doTextTest("class Foo {\n" +
               "    /**\n" +
               "     @param i\n" +
               "     @param j\n" +
               "    */\n" +
               "    void foo(int i, int j) {\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    /**\n" +
     "     @param i\n" +
     "     @param j\n" +
     "     */\n" +
     "    void foo(int i, int j) {\n" +
     "    }\n" +
     "}");
  }

  public void testBinaryExpression() {
    getSettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "        if (event.isConsumed() &&\n" +
               "condition2) {\n" +
               "      return;\n" +
               "    }\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        if (event.isConsumed() &&\n" +
     "            condition2) {\n" +
     "            return;\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testCaseBraces() {
    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        switch (a) {\n" +
               "            case 0: {\n" +
               "            }\n" +
               "        }\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        switch (a) {\n" +
     "            case 0: {\n" +
     "            }\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testFormatCodeFragment() {
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(getProject());
    final PsiCodeFragment fragment = factory.createCodeBlockCodeFragment("a=1;int b=2;", null, true);
    final PsiElement[] result = new PsiElement[1];

    CommandProcessor.getInstance().executeCommand(getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        result[0] = CodeStyleManager.getInstance(getProject()).reformat(fragment);
      }
      catch (IncorrectOperationException e) {
        fail(e.getLocalizedMessage());
      }
    }), null, null);

    assertEquals("a = 1;\n" + "int b = 2;", result[0].getText());
  }

  public void testNewLineAfterJavaDocs() {
    String before = "/** @noinspection InstanceVariableNamingConvention*/class Foo{\n" +
                    "/** @noinspection InstanceVariableNamingConvention*/int myFoo;\n" +
                    "/** @noinspection InstanceVariableNamingConvention*/ void foo(){}}";

    String after = "/**\n" +
                   " * @noinspection InstanceVariableNamingConvention\n" +
                   " */\n" +
                   "class Foo {\n" +
                   "    /**\n" +
                   "     * @noinspection InstanceVariableNamingConvention\n" +
                   "     */\n" +
                   "    int myFoo;\n" +
                   "\n" +
                   "    /**\n" +
                   "     * @noinspection InstanceVariableNamingConvention\n" +
                   "     */\n" +
                   "    void foo() {\n" +
                   "    }\n" +
                   "}";

    doTextTest(before, after);
  }

  public void testArrayInitializerWrapping() {
    getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false;
    getSettings().RIGHT_MARGIN = 37;

    doTextTest("class Foo{\n" +
               "    public int[] i = new int[]{1,2,3,4,5,6,7,8,9};\n" +
               "    void foo() {\n" +
               "        foo(new int[]{1,2,3,4,5,6,7,8,9});\n" +
               "    }" +
"}", "class Foo {\n" +
     "    public int[] i = new int[]{1, 2,\n" +
     "            3, 4, 5, 6, 7, 8, 9};\n" +
     "\n" +
     "    void foo() {\n" +
     "        foo(new int[]{1, 2, 3, 4, 5,\n" +
     "                6, 7, 8, 9});\n" +
     "    }\n" +
     "}");

    getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doTextTest("class Foo{\n" +
               "    public int[] i = new int[]{1,2,3,4,5,6,7,8,9};\n" +
               "    void foo() {\n" +
               "        foo(new int[]{1,2,3,4,5,6,7,8,9});\n" +
               "    }" +
"}", "class Foo {\n" +
     "    public int[] i = new int[]{1, 2,\n" +
     "                               3, 4,\n" +
     "                               5, 6,\n" +
     "                               7, 8,\n" +
     "                               9};\n" +
     "\n" +
     "    void foo() {\n" +
     "        foo(new int[]{1, 2, 3, 4, 5,\n" +
     "                      6, 7, 8, 9});\n" +
     "    }\n" +
     "}");

  }

  public void testJavaDocIndentation() {
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 2;
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 2;
    getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA).TAB_SIZE = 4;

    getJavaSettings().ENABLE_JAVADOC_FORMATTING = false;

    doTextTest("public interface PsiParser {\n" +
               "  /**\n" +
               "   * Parses the contents of the specified PSI builder and returns an AST tree with the\n" +
               "   * specified type of root element. The PSI builder contents is the entire file\n" +
               "   * or (if chameleon tokens are used) the text of a chameleon token which needs to\n" +
               "   * be reparsed.\n" +
               "   * @param root the type of the root element in the AST tree.\n" +
               "   * @param builder the builder which is used to retrieve the original file tokens and build the AST tree.\n" +
               "   * @return the root of the resulting AST tree.\n" +
               "   */\n" +
               "  ASTNode parse(IElementType root, PsiBuilder builder);\n" +
"}", "public interface PsiParser {\n" +
     "  /**\n" +
     "   * Parses the contents of the specified PSI builder and returns an AST tree with the\n" +
     "   * specified type of root element. The PSI builder contents is the entire file\n" +
     "   * or (if chameleon tokens are used) the text of a chameleon token which needs to\n" +
     "   * be reparsed.\n" +
     "   * @param root the type of the root element in the AST tree.\n" +
     "   * @param builder the builder which is used to retrieve the original file tokens and build the AST tree.\n" +
     "   * @return the root of the resulting AST tree.\n" +
     "   */\n" +
     "  ASTNode parse(IElementType root, PsiBuilder builder);\n" +
     "}");
  }

  public void testRemoveLineBreak() {
    getSettings().KEEP_LINE_BREAKS = true;
    getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;

    doTextTest("class A\n" + "{\n" + "}", "class A {\n" + "}");

    doTextTest("class A {\n" + "    void foo()\n" + "    {\n" + "    }\n" + "}", "class A {\n" + "    void foo() {\n" + "    }\n" + "}");

    doTextTest("class A {\n" + "    void foo()\n" + "    {\n" + "        if (a)\n" + "        {\n" + "        }\n" + "    }\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        if (a) {\n" + "        }\n" + "    }\n" + "}");

  }

  public void testBlankLines() {
    getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 0;
    getSettings().KEEP_BLANK_LINES_IN_CODE = 0;
    getSettings().KEEP_BLANK_LINES_BEFORE_RBRACE = 0;
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 0;
    getSettings().BLANK_LINES_AFTER_IMPORTS = 0;
    getSettings().BLANK_LINES_AFTER_PACKAGE = 0;
    getSettings().BLANK_LINES_AROUND_CLASS = 0;
    getSettings().BLANK_LINES_AROUND_FIELD = 0;
    getSettings().BLANK_LINES_AROUND_METHOD = 0;
    getSettings().BLANK_LINES_BEFORE_IMPORTS = 0;
    getSettings().BLANK_LINES_BEFORE_PACKAGE = 0;

    getSettings().BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 2;
    getSettings().BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 3;

    doTextTest("/*\n" +
               " * This is a sample file.\n" +
               " */\n" +
               "package com.intellij.samples;\n" +
               "\n" +
               "import com.intellij.idea.Main;\n" +
               "\n" +
               "import javax.swing.*;\n" +
               "import java.util.Vector;\n" +
               "\n" +
               "public class Foo {\n" +
               "    private int field1;\n" +
               "    private int field2;\n" +
               "\n" +
               "    public void foo1() {\n" +
               "\n" +
               "    }\n" +
               "\n" +
               "    public void foo2() {\n" +
               "\n" +
               "    }\n" +
               "\n" +
               "}",


               "/*\n" +
               " * This is a sample file.\n" +
               " */\n" +
               "package com.intellij.samples;\n" +
               "import com.intellij.idea.Main;\n" +
               "\n" +
               "import javax.swing.*;\n" +
               "import java.util.Vector;\n" +
               "public class Foo {\n" +
               "    private int field1;\n" +
               "    private int field2;\n" +
               "    public void foo1() {\n" +
               "    }\n" +
               "    public void foo2() {\n" +
               "    }\n" +
               "}");

    doTextTest("interface Foo {\n" +
               "    int field1;\n" +
               "    int field2;\n" +
               "\n" +
               "    void foo1();\n" +
               "\n" +
               "    void foo2();\n" +
               "\n" +
               "}",


               "interface Foo {\n" +
               "    int field1;\n" +
               "\n" +
               "\n" +
               "    int field2;\n" +
               "\n" +
               "\n" +
               "\n" +
               "    void foo1();\n" +
               "\n" +
               "\n" +
               "\n" +
               "    void foo2();\n" +
               "}");

  }

  public void testStaticBlockBraces() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}");

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}");

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static\n" + "    {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}");


    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "        }\n" + "}",
               "class Foo {\n" + "    static\n" + "        {\n" + "        //comment\n" + "        i = foo();\n" + "        }\n" + "}");


    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                      "    static\n" +
                                                                                                                      "        {\n" +
                                                                                                                      "            //comment\n" +
                                                                                                                      "            i = foo();\n" +
                                                                                                                      "        }\n" +
                                                                                                                      "}");


  }

  public void testBraces2() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "         if (clientSocket == null)\n" +
               "        {\n" +
               "            return false;\n" +
               "        }" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        if (clientSocket == null) {\n" +
     "            return false;\n" +
     "        }\n" +
     "    }\n" +
     "}");

    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "         for (int i = 0; i < 10; i++)\n" +
               "        {\n" +
               "            return false;\n" +
               "        }" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        for (int i = 0; i < 10; i++) {\n" +
     "            return false;\n" +
     "        }\n" +
     "    }\n" +
     "}");


    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "         for (Object i : collection)\n" +
               "        {\n" +
               "            return false;\n" +
               "        }" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        for (Object i : collection) {\n" +
     "            return false;\n" +
     "        }\n" +
     "    }\n" +
     "}");


    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "         while (i  >0)\n" +
               "        {\n" +
               "            return false;\n" +
               "        }" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        while (i > 0) {\n" +
     "            return false;\n" +
     "        }\n" +
     "    }\n" +
     "}");

    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;

    doTextTest("class Foo{\n" + "    /**\n" + "     *\n" + "     */\n" + "    void foo() {\n" + "    }\n" + "}",
               "class Foo {\n" + "    /**\n" + "     *\n" + "     */\n" + "    void foo() {\n" + "    }\n" + "}");


    getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;

    doTextTest("/**\n" + " *\n" + " */\n" + "class Foo\n{\n" + "}", "/**\n" + " *\n" + " */\n" + "class Foo {\n" + "}");

    doTextTest("/**\n" + " *\n" + " */\n" + "class Foo\n extends B\n{\n" + "}",
               "/**\n" + " *\n" + " */\n" + "class Foo\n        extends B\n" + "{\n" + "}");

  }

  public void testSynchronized() {

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this) {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "        }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}");

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this)\n" +
                                                                                                                   "        {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "        }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}");

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this)\n" +
                                                                                                                   "            {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "            }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}");


    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
    doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {\n" + "foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                          "    void foo() {\n" +
                                                                                                                          "        synchronized (this)\n" +
                                                                                                                          "            {\n" +
                                                                                                                          "                foo();\n" +
                                                                                                                          "            }\n" +
                                                                                                                          "    }\n" +
                                                                                                                          "}");

  }

  public void testNextLineShiftedForBlockStatement() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;

    doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a)\n" + "        foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        if (a)\n" + "            foo();\n" + "    }\n" + "}");
  }

  public void testFieldWithJavadocAndAnnotation() {
    doTextTest("class Foo {\n" + "    /**\n" + "     * java doc\n" + "     */\n" + "    @NoInspection\n" + "    String field;\n" + "}",
               "class Foo {\n" + "    /**\n" + "     * java doc\n" + "     */\n" + "    @NoInspection\n" + "    String field;\n" + "}");
  }

  public void testLongCallChainAfterElse() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    getSettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().ELSE_ON_NEW_LINE = false;
    getSettings().RIGHT_MARGIN = 110;
    getSettings().KEEP_LINE_BREAKS = false;
    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "        if (types.length > 1) // returns multiple columns\n" +
               "        {\n" +
               "        } else\n" +
               "            result.add(initializeObject(os, types[0], initializeCollections, initializeAssociations, initializeChildren));" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        if (types.length > 1) // returns multiple columns\n" +
     "        {\n" +
     "        } else\n" +
     "            result.add(initializeObject(os, types[0], initializeCollections, initializeAssociations, initializeChildren));\n" +
     "    }\n" +
     "}");
  }

  public void testSpacesIncode() {

    final JavaPsiFacade facade = getJavaFacade();
    final LanguageLevel level = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();

    LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);

    try {
      doTextTest("class C<Y, X> {\n" + "}", "class C<Y, X> {\n" + "}");

      getSettings().SPACE_BEFORE_METHOD_LBRACE = true;
      getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;

      doTextTest("enum En {\n" + "    A(10) {},\n" + "    B(10) {},\n" + "    C(10);\n" + "\n" + "    En(int i) { }\n" + "}",
                 "enum En {\n" + "    A(10) {},\n" + "    B(10) {},\n" + "    C(10);\n" + "\n" + "    En(int i) { }\n" + "}");

      doTextTest("class C {\n" +
                 "    void foo (Map<?, String> s) {\n" +
                 "        Set<? extends Map<?, String>.Entry<?, String>> temp = s.entries();\n" +
                 "    }\n" +
"}", "class C {\n" +
     "    void foo(Map<?, String> s) {\n" +
     "        Set<? extends Map<?, String>.Entry<?, String>> temp = s.entries();\n" +
     "    }\n" +
     "}");

      doTextTest("class B {\n" +
                 "    public final A<String> myDelegate = new A<String>();\n" +
                 "\n" +
                 "    public List<? extends String> method1() {\n" +
                 "        return myDelegate.method1();\n" +
                 "    }\n" +
                 "\n" +
                 "    public String method2(String t) {\n" +
                 "        return myDelegate.method2(t);\n" +
                 "    }\n" +
"}", "class B {\n" +
     "    public final A<String> myDelegate = new A<String>();\n" +
     "\n" +
     "    public List<? extends String> method1() {\n" +
     "        return myDelegate.method1();\n" +
     "    }\n" +
     "\n" +
     "    public String method2(String t) {\n" +
     "        return myDelegate.method2(t);\n" +
     "    }\n" +
     "}");
    }
    finally {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(level);
    }

  }

  ///IDEA-7761
  public void testKeepBlankLineInCodeBeforeComment() {
    getSettings().KEEP_BLANK_LINES_IN_CODE = 1;
    getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 0;
    getSettings().KEEP_FIRST_COLUMN_COMMENT = false;

    doTextTest("public class ReformatProblem {\n" +
               "\n" +
               "    //comment in declaration\n" +
               "    public static void main(String[] args) {\n" +
               "        for (String arg : args) {\n" +
               "            \n" +
               "            // a first system out\n" +
               "            System.out.println(\"\");\n" +
               "            \n" +
               "            // another system out\n" +
               "            System.out.println(\"arg = \" + arg);\n" +
               "        }\n" +
               "    }\n" +
"}", "public class ReformatProblem {\n" +
     "    //comment in declaration\n" +
     "    public static void main(String[] args) {\n" +
     "        for (String arg : args) {\n" +
     "\n" +
     "            // a first system out\n" +
     "            System.out.println(\"\");\n" +
     "\n" +
     "            // another system out\n" +
     "            System.out.println(\"arg = \" + arg);\n" +
     "        }\n" +
     "    }\n" +
     "}");

    getSettings().KEEP_BLANK_LINES_IN_CODE = 0;
    getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS = 1;
    getSettings().KEEP_FIRST_COLUMN_COMMENT = false;

    doTextTest("public class ReformatProblem {\n" +
               "\n" +
               "    //comment in declaration\n" +
               "    public static void main(String[] args) {\n" +
               "        for (String arg : args) {\n" +
               "            \n" +
               "            // a first system out\n" +
               "            System.out.println(\"\");\n" +
               "            \n" +
               "            // another system out\n" +
               "            System.out.println(\"arg = \" + arg);\n" +
               "        }\n" +
               "    }\n" +
"}", "public class ReformatProblem {\n" +
     "\n" +
     "    //comment in declaration\n" +
     "    public static void main(String[] args) {\n" +
     "        for (String arg : args) {\n" +
     "            // a first system out\n" +
     "            System.out.println(\"\");\n" +
     "            // another system out\n" +
     "            System.out.println(\"arg = \" + arg);\n" +
     "        }\n" +
     "    }\n" +
     "}");

  }

  public void testSpaceBeforeTryBrace() {
    getSettings().SPACE_BEFORE_TRY_LBRACE = false;
    getSettings().SPACE_BEFORE_FINALLY_LBRACE = true;
    doTextTest("class Foo{\n" + "    void foo() {\n" + "        try {\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        try{\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}");

    getSettings().SPACE_BEFORE_TRY_LBRACE = true;
    getSettings().SPACE_BEFORE_FINALLY_LBRACE = false;

    doTextTest("class Foo{\n" + "    void foo() {\n" + "        try {\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        try {\n" + "        } finally{\n" + "        }\n" + "    }\n" + "}");

  }

  public void testFormatComments() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    doTextTest("public class Test {\n" + "\n" + "    /**\n" + "     * The s property.\n" + "     */\n" + "    private String s;\n" + "}",
               "public class Test {\n" + "\n" + "    /**\n" + "     * The s property.\n" + "     */\n" + "    private String s;\n" + "}");

  }

  public void testDoNotWrapLBrace() throws IncorrectOperationException {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getSettings().RIGHT_MARGIN = 66;
    doTextTest("public class Test {\n" +
               "    void foo(){\n" +
               "        if (veryLongIdentifier1 == 1 && veryLongIdentifier2 == 2) {\n" +
               "            doSmth();\n" +
               "        }\n" +
               "    }\n" +
"}", "public class Test {\n" +
     "    void foo() {\n" +
     "        if (veryLongIdentifier1 == 1 && veryLongIdentifier2 == 2) {\n" +
     "            doSmth();\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testNewLinesAroundArrayInitializer() throws IncorrectOperationException {
    getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true;
    getSettings().ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true;
    getSettings().RIGHT_MARGIN = 40;
    doTextTest("class Foo {\n" + "    int[] a = new int[]{1,2,0x0052,0x0053,0x0054,0x0054,0x0054};\n" + "}", "class Foo {\n" +
                                                                                                             "    int[] a = new int[]{\n" +
                                                                                                             "            1, 2, 0x0052, 0x0053,\n" +
                                                                                                             "            0x0054, 0x0054, 0x0054\n" +
                                                                                                             "    };\n" +
                                                                                                             "}");
  }

  public void testSpaceAfterCommaInEnum() throws IncorrectOperationException {
    getSettings().SPACE_AFTER_COMMA = true;

    final JavaPsiFacade facade = getJavaFacade();
    final LanguageLevel effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    try {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);


      doTextTest("public enum StringExDirection {\n" + "\n" + "    RIGHT_TO_LEFT, LEFT_TO_RIGHT\n" + "\n" + "}",
                 "public enum StringExDirection {\n" + "\n" + "    RIGHT_TO_LEFT, LEFT_TO_RIGHT\n" + "\n" + "}");
    }
    finally {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(effectiveLanguageLevel);
    }
  }

  public void testRemoveBraceBeforeInstanceOf() throws IncorrectOperationException {
    doTextTest("class ReformatInstanceOf {\n" +
               "    void foo(Object string) {\n" +
               "        if (string.toString() instanceof String) {} // reformat me\n" +
               "    }\n" +
"}", "class ReformatInstanceOf {\n" +
     "    void foo(Object string) {\n" +
     "        if (string.toString() instanceof String) {\n" +
     "        } // reformat me\n" +
     "    }\n" +
     "}");
  }

  public void testAnnotationBeforePackageLocalConstructor() throws IncorrectOperationException {
    doTextTest("class Foo {\n" + "    @MyAnnotation Foo() {\n" + "    }\n" + "}",
               "class Foo {\n" + "    @MyAnnotation\n" + "    Foo() {\n" + "    }\n" + "}");
  }

  public void testLongAnnotationsAreNotWrapped() {
    getSettings().ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTest();
  }

  public void testWrapExtendsList() {
    getSettings().RIGHT_MARGIN = 50;
    getSettings().EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    getSettings().EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

    doTextTest("class ColtreDataProvider extends DataProvider, AgentEventListener, ParameterDataEventListener {\n}",
               "class ColtreDataProvider extends DataProvider,\n" +
               "        AgentEventListener,\n" +
               "        ParameterDataEventListener {\n}");
  }

  public void testWrapLongExpression() {
    getSettings().RIGHT_MARGIN = 80;
    getSettings().BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTextTest("class Foo {\n" +
               "    void foo () {\n" +
               "        return (interval1.getEndIndex() >= interval2.getStartIndex() && interval3.getStartIndex() <= interval4.getEndIndex()) || (interval5.getEndIndex() >= interval6.getStartIndex() && interval7.getStartIndex() <= interval8.getEndIndex());" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        return (interval1.getEndIndex() >= interval2.getStartIndex() &&\n" +
                    "                interval3.getStartIndex() <= interval4.getEndIndex()) ||\n" +
                    "               (interval5.getEndIndex() >= interval6.getStartIndex() &&\n" +
                    "                interval7.getStartIndex() <= interval8.getEndIndex());\n" +
                    "    }\n" +
                    "}");
  }

  public void testDoNotWrapCallChainIfParametersWrapped() {
    getSettings().RIGHT_MARGIN = 87;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    //getSettings().PREFER_PARAMETERS_WRAP = true;

    doMethodTest(
      //9                    30                            70         80    86
      "descriptors = manager.createProblemDescriptor(parameter1, parameter2, parameterparameterpar3,parameter4);",

      "descriptors = manager.createProblemDescriptor(parameter1, parameter2,\n" +
      "                                              parameterparameterpar3,\n" +
      "                                              parameter4);"

    );
  }

  public void testAlignTernaryOperation() {
    getSettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doMethodTest("String s = x == 0 ? \"hello\" :\n" +
                 "                x == 5 ? \"something else\" :\n" +
                 "                        x > 0 ? \"bla, bla\" :\n" +
                 "                                \"\";", "String s = x == 0 ? \"hello\" :\n" +
                                                          "           x == 5 ? \"something else\" :\n" +
                                                          "           x > 0 ? \"bla, bla\" :\n" +
                                                          "           \"\";");

    getSettings().TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = true;

    doMethodTest("int someVariable = a ?\n" + "x :\n" + "y;",
                 "int someVariable = a ?\n" + "                   x :\n" + "                   y;");
  }

  public void testRightMargin_2() {
    getSettings().RIGHT_MARGIN = 65;
    getSettings().ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = true;
    getSettings().KEEP_LINE_BREAKS = false;

    doClassTest(
      "public static final Map<LongType, LongType> longVariableName =\n" +
      "variableValue;",
      "public static final Map<LongType, LongType> longVariableName\n" +
      "        = variableValue;");
  }

  public void testRightMargin_3() {
    getSettings().RIGHT_MARGIN = 65;
    getSettings().ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false;
    getSettings().KEEP_LINE_BREAKS = false;

    doClassTest(
      "public static final Map<LongType, LongType> longVariableName =\n" +
      "variableValue;",
      "public static final Map<LongType, LongType>\n" +
      "        longVariableName = variableValue;");
  }

  public void testDoNotRemoveLineBreaksBetweenComments(){
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().KEEP_FIRST_COLUMN_COMMENT = false;

    doTextTest(
      "public class Foo {\n" +
      "   //here is a comment\n" +
      "   //line 2 of comment\n" +
      "   public void myMethod() {\n" +
      "       //a comment\n" +
      "       //... another comment\n" +
      "   }\n" +
      "\n" +
      "//save for later\n" +
      "//    public void incompleteMethod() {\n" +
      "//        int blah = 0;\n" +
      "//        callSomeMethod();\n" +
      "//        callSomeOtherMethod();\n" +
      "//        doSomethingElse();\n" +
      "//    }\n" +
      "\n" +
      "//comment at first line\n" +
      "}",
      "public class Foo {\n" +
      "    //here is a comment\n" +
      "    //line 2 of comment\n" +
      "    public void myMethod() {\n" +
      "        //a comment\n" +
      "        //... another comment\n" +
      "    }\n" +
      "\n" +
      "    //save for later\n" +
      "    //    public void incompleteMethod() {\n" +
      "    //        int blah = 0;\n" +
      "    //        callSomeMethod();\n" +
      "    //        callSomeOtherMethod();\n" +
      "    //        doSomethingElse();\n" +
      "    //    }\n" +
      "\n" +
      "    //comment at first line\n" +
      "}");
  }

  public void testWrapParamsOnEveryItem() {
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject());

    int oldMargin = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE).RIGHT_MARGIN;
    boolean oldKeep = codeStyleSettings.KEEP_LINE_BREAKS;
    int oldWrap = codeStyleSettings.METHOD_PARAMETERS_WRAP;

    try {
      codeStyleSettings.setRightMargin(JavaLanguage.INSTANCE, 80);
      codeStyleSettings.KEEP_LINE_BREAKS = false;
      codeStyleSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

      doClassTest(
        "public void foo(String p1,\n" +
        "                String p2,\n" +
        "                String p3,\n" +
        "                String p4,\n" +
        "                String p5,\n" +
        "                String p6,\n" +
        "                String p7) {\n" +
        "    //To change body of implemented methods use File | Settings | File Templates.\n" +
        "}",
        "public void foo(String p1,\n" +
        "                String p2,\n" +
        "                String p3,\n" +
        "                String p4,\n" +
        "                String p5,\n" +
        "                String p6,\n" +
        "                String p7) {\n" +
        "    //To change body of implemented methods use File | Settings | File Templates.\n" +
        "}");
    }
    finally {
      codeStyleSettings.setRightMargin(JavaLanguage.INSTANCE, oldMargin);
      codeStyleSettings.KEEP_LINE_BREAKS = oldKeep;
      codeStyleSettings.METHOD_PARAMETERS_WRAP = oldWrap;
    }

  }

  public void testCommentAfterDeclaration() {
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);

    int oldMargin = codeStyleSettings.getDefaultRightMargin();
    int oldWrap = javaSettings.ASSIGNMENT_WRAP;

    try {
      codeStyleSettings.setDefaultRightMargin(20);
      javaSettings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
      doMethodTest(
        "int i=0; //comment comment",
        "int i =\n" +
        "        0; //comment comment"
      );

    }
    finally {
      codeStyleSettings.setDefaultRightMargin(oldMargin);
      javaSettings.ASSIGNMENT_WRAP = oldWrap;
    }
  }

  // ------------------------------------------------
  //              Tickets-implied tests
  // ------------------------------------------------

  public void testSCR915() {
    getSettings().SPACE_AROUND_ADDITIVE_OPERATORS = false;
    doTest("SCR915.java", "SCR915_after.java");
  }

  public void testSCR429() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.KEEP_BLANK_LINES_IN_CODE = 2;
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 2;
    doTest();
  }

  public void testSCR548() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 4;
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 2;
    doTest();
  }

  public void testIDEA_16151() {
    doClassTest("@ValidateNestedProperties({\n" +
                "@Validate(field=\"name\", required=true, maxlength=Trip.NAME),\n" +
                "@Validate(field=\"name\", required=true, maxlength=Trip.NAME)\n" +
                "})" +
                "public Trip getTrip() {\n" +
                "}", "@ValidateNestedProperties({\n" +
                     "        @Validate(field = \"name\", required = true, maxlength = Trip.NAME),\n" +
                     "        @Validate(field = \"name\", required = true, maxlength = Trip.NAME)\n" +
                     "})\n" +
                     "public Trip getTrip() {\n" +
                     "}");

  }

  public void testIDEA_14324() {
    getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doClassTest(
      "@Ann1({ @Ann2,\n" +
      "                      @Ann3})\n" +
      "public AuthorAddress getAddress() {\n" +
      "    return address;\n" +
      "}",
      "@Ann1({@Ann2,\n" +
      "       @Ann3})\n" +
      "public AuthorAddress getAddress() {\n" +
      "    return address;\n" +
      "}");

    doClassTest("@AttributeOverrides({ @AttributeOverride(name = \"address\", column = @Column(name = \"ADDR\")),\n" +
                "                      @AttributeOverride(name = \"country\", column = @Column(name = \"NATION\")) })\n" +
                "public AuthorAddress getAddress() {\n" +
                "    return address;\n" +
                "}",
                "@AttributeOverrides({@AttributeOverride(name = \"address\", column = @Column(name = \"ADDR\")),\n" +
                "                     @AttributeOverride(name = \"country\", column = @Column(name = \"NATION\"))})\n" +
                "public AuthorAddress getAddress() {\n" +
                "    return address;\n" +
                "}"

    );
  }

public void testSCR260() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    settings.KEEP_LINE_BREAKS = false;
    doTest();
  }

  public void testSCR114() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    settings.CATCH_ON_NEW_LINE = true;
    doTest();
  }

  public void testSCR259() {
    myTextRange = new TextRange(36, 60);
    final CommonCodeStyleSettings settings = getSettings();
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    settings.KEEP_LINE_BREAKS = false;
    doTest();
  }

  public void testSCR279() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
  }

  public void testSCR395() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    doTest();
  }

  public void testSCR11799() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 4;
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  public void testSCR501() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.KEEP_FIRST_COLUMN_COMMENT = true;
    doTest();
  }

  public void testSCR879() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  

  public void testSCR547() {
    doTextTest("class Foo { \n" +
               "    Object[] objs = { \n" +
               "            new Object() { \n" +
               "        public String toString() { \n" +
               "            return \"x\"; \n" +
               "        } \n" +
               "    } \n" +
               "    }; \n" +
"}", "class Foo {\n" +
     "    Object[] objs = {\n" +
     "            new Object() {\n" +
     "                public String toString() {\n" +
     "                    return \"x\";\n" +
     "                }\n" +
     "            }\n" +
     "    };\n" +
     "}");
  }

  public void testSCR479() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.RIGHT_MARGIN = 80;
    settings.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTextTest("public class Foo {\n" +
               "    public static void main(String[] args) {\n" +
               "        if (name != null ?                !name.equals(that.name) : that.name !=                null)\n" +
               "            return false;\n" +
               "    }\n" +
"}", "public class Foo {\n" +
     "    public static void main(String[] args) {\n" +
     "        if (name != null ? !name.equals(that.name) : that.name != null)\n" +
     "            return false;\n" +
     "    }\n" +
     "}");
  }

  public void testSCR190() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.KEEP_LINE_BREAKS = false;
    doTextTest("public class EntityObject \n" +
               "{ \n" +
               "    private Integer id; \n" +
               "\n" +
               "    public Integer getId() \n" +
               "    { \n" +
               "        return id; \n" +
               "    } \n" +
               "\n" +
               "    public void setId(Integer id) \n" +
               "    { \n" +
               "        this.id = id; \n" +
               "    } \n" +
"}", "public class EntityObject {\n" +
     "    private Integer id;\n" +
     "\n" +
     "    public Integer getId() {\n" +
     "        return id;\n" +
     "    }\n" +
     "\n" +
     "    public void setId(Integer id) {\n" +
     "        this.id = id;\n" +
     "    }\n" +
     "}");
  }

  public void testSCR1535() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTextTest("public class Foo {\n" +
               "    public int foo() {\n" +
               "        if (a) {\n" +
               "            return;\n" +
               "        }\n" +
               "    }\n" +
"}", "public class Foo\n" +
     "{\n" +
     "    public int foo()\n" +
     "    {\n" +
     "        if (a)\n" +
     "        {\n" +
     "            return;\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testSCR970() {
    final CommonCodeStyleSettings settings = getSettings();
    settings.THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    settings.THROWS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTest();
  }

  public void testSCR1486() {
    doTextTest("public class Test {\n" + "  private BigDecimal\n" + "}", "public class Test {\n" + "    private BigDecimal\n" + "}");

    doTextTest("public class Test {\n" + "  @NotNull private BigDecimal\n" + "}",
               "public class Test {\n" + "    @NotNull\n" + "    private BigDecimal\n" + "}");

  }

  public void test1607() {
    getSettings().RIGHT_MARGIN = 30;
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().ALIGN_MULTILINE_PARAMETERS = true;
    getSettings().METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    doTextTest("class TEst {\n" + "void foo(A a,B b){ /* compiled code */ }\n" + "}",
               "class TEst {\n" + "    void foo(A a, B b)\n" + "    { /* compiled code */ }\n" + "}");
  }

  public void testSCR1615() {
    getSettings().CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;

    doTextTest(
      "public class ZZZZ \n" +
      "   { \n" +
      "   public ZZZZ() \n" +
      "      { \n" +
      "      if (a){\n" +
      "foo();}\n" +
      "      } \n" +
      "   }",
      "public class ZZZZ\n" +
      "    {\n" +
      "    public ZZZZ()\n" +
      "        {\n" +
      "        if (a)\n" +
      "            {\n" +
      "            foo();\n" +
      "            }\n" +
      "        }\n" +
      "    }");
  }

  public void testSCR1047() {
    doTextTest("class Foo{\n" + "    void foo(){\n" + "        String field1, field2;\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        String field1, field2;\n" + "    }\n" + "}");
  }

  public void testSCR524() {
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doTextTest("class Foo {\n" + "    void foo() { return;}" + "}", "class Foo {\n" + "    void foo() { return;}\n" + "}");

    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2;
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;
    getSettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    getSettings().METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    getSettings().CASE_STATEMENT_ON_NEW_LINE = false;
    
    doTextTest("class Foo{\n" +
               "void foo() {\n" +
               "if(a) {return;}\n" +
               "for(a = 0; a < 10; a++) {return;}\n" +
               "switch(a) {case 1: return;}\n" +
               "do{return;} while (a);\n" +
               "while(a){return;}\n" +
               "try{return;} catch(Ex e){return;} finally{return;}\n" +
               "}\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        if (a) {return;}\n" +
     "        for (a = 0; a < 10; a++) {return;}\n" +
     "        switch (a) {case 1: return;}\n" +
     "        do {return;} while (a);\n" +
     "        while (a) {return;}\n" +
     "        try {return;} catch (Ex e) {return;} finally {return;}\n" +
     "    }\n" +
     "}");
  }

  public void testSCR3062() {
    getSettings().KEEP_LINE_BREAKS = false;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    getSettings().RIGHT_MARGIN = 80;

    getSettings().PREFER_PARAMETERS_WRAP = true;

    doTextTest(
     "public class Foo { \n" +
     "    public static void main() { \n" +
     "        foo.foobelize().foobelize().foobelize().bar(\"The quick brown\", \n" +
     "                                                    \"fox jumped over\", \n" +
     "                                                    \"the lazy\", \"dog\"); \n" +
     "    } \n" +
    "}",
     "public class Foo {\n" +
     "    public static void main() {\n" +
     "        foo.foobelize().foobelize().foobelize().bar(\"The quick brown\",\n" +
     "                                                    \"fox jumped over\",\n" +
     "                                                    \"the lazy\", \"dog\");\n" +
     "    }\n" +
     "}");

    getSettings().PREFER_PARAMETERS_WRAP = false;

    doTextTest(
      "public class Foo { \n" +
      "    public static void main() { \n" +
      "        foo.foobelize().foobelize().foobelize().bar(\"The quick brown\", \n" +
      "                                                    \"fox jumped over\", \n" +
      "                                                    \"the lazy\", \"dog\"); \n" +
      "    } \n" +
      "}",
      "public class Foo {\n" +
      "    public static void main() {\n" +
      "        foo.foobelize().foobelize().foobelize()\n" +
      "                .bar(\"The quick brown\", \"fox jumped over\", \"the lazy\", \"dog\");\n" +
      "    }\n" +
      "}");

  }

  public void testSCR1658() {
    doTextTest("/** \n" + " * @author\tMike\n" + " */\n" + "public class Foo {\n" + "}",
               "/**\n" + " * @author Mike\n" + " */\n" + "public class Foo {\n" + "}");
  }

  public void testSCR1699() {
    doTextTest("class Test {\n" + "    Test(String t1 , String t2) {\n" + "    }\n" + "}",
               "class Test {\n" + "    Test(String t1, String t2) {\n" + "    }\n" + "}");
  }

  public void testSCR1700() {
    doTextTest("class Test {\n" + "    Test(String      t1 , String      t2) {\n" + "    }\n" + "}",
               "class Test {\n" + "    Test(String t1, String t2) {\n" + "    }\n" + "}");
  }

  public void testSCR1701() {
    getSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    getSettings().SPACE_WITHIN_METHOD_PARENTHESES = false;
    getSettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getSettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getSettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    doTextTest("class Foo {\n" + "    void foo() {\n" + "        foo(a,b);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        foo( a, b );\n" + "    }\n" + "}");
  }

  public void testSCR1703() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        for (Object o : localizations) {\n" +
               "            //do something \n" +
               "        }\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        for (Object o : localizations)\n" +
     "        {\n" +
     "            //do something \n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testSCR1804() {
    getSettings().ALIGN_MULTILINE_ASSIGNMENT = true;
    doTextTest(
      "class Foo {\n" + "    void foo() {\n" + "        int i;\n" + "        i = \n" + "                1 + 2;\n" + "    }\n" + "}",
      "class Foo {\n" + "    void foo() {\n" + "        int i;\n" + "        i =\n" + "                1 + 2;\n" + "    }\n" + "}");

    doTextTest("class Foo {\n" + "    void foo() {\n" + "        i = j =\n" + "        k = l = 1 + 2;\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        i = j =\n" + "        k = l = 1 + 2;\n" + "    }\n" + "}");

  }

  public void testSCR1795() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    doTextTest("public class Test {\n" +
               "    public static void main(String[] args) {\n" +
               "        do {\n" +
               "            // ...\n" +
               "        } while (true);\n" +
               "    }\n" +
"}", "public class Test {\n" +
     "    public static void main(String[] args) {\n" +
     "        do {\n" +
     "            // ...\n" +
     "        } while (true);\n" +
     "    }\n" +
     "}");
  }

  public void testSCR1936() {
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 4;
    doTextTest("/**\n" + " * Foo - test class\n" + " */\n" + "class Foo{\n" + "}",
               "/**\n" + " * Foo - test class\n" + " */\n" + "class Foo {\n" + "\n" + "\n" + "\n" + "\n" + "}");

    doTextTest("/**\n" + " * Foo - test class\n" + " */\n" + "class Foo{\n" + "    int myFoo;\n" + "}",
               "/**\n" + " * Foo - test class\n" + " */\n" + "class Foo {\n" + "\n" + "\n" + "\n" + "\n" + "    int myFoo;\n" + "}");

  }

  public void test1980() {
    getSettings().RIGHT_MARGIN = 144;
    getSettings().TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    getSettings().TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = true;
    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "final VirtualFile moduleRoot = moduleRelativePath.equals(\"\") ? projectRootDirAfter : projectRootDirAfter.findFileByRelativePath(moduleRelativePath);\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        final VirtualFile moduleRoot = moduleRelativePath.equals(\"\")\n" +
     "                                       ? projectRootDirAfter\n" +
     "                                       : projectRootDirAfter.findFileByRelativePath(moduleRelativePath);\n" +
     "    }\n" +
     "}");
  }

    public void testSCR2089() {
    doTextTest("class Test { \n" +
               "    void test(int i) { \n" +
               "        switch (i) { \n" +
               "            case 1: { \n" +
               "                int x = 0; \n" +
               "                System.out.println(x); \n" +
               "            } \n" +
               "                break; \n" +
               "            case 2: { \n" +
               "                int y = 0; \n" +
               "                System.out.println(y); \n" +
               "            } \n" +
               "                break; \n" +
               "        } \n" +
               "    } \n" +
"}", "class Test {\n" +
     "    void test(int i) {\n" +
     "        switch (i) {\n" +
     "            case 1: {\n" +
     "                int x = 0;\n" +
     "                System.out.println(x);\n" +
     "            }\n" +
     "            break;\n" +
     "            case 2: {\n" +
     "                int y = 0;\n" +
     "                System.out.println(y);\n" +
     "            }\n" +
     "            break;\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testSCR2132() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED;
    getSettings().ELSE_ON_NEW_LINE = true;

    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "        if (!rightPanel.isAncestorOf(validationPanel)) \n" +
               "                {\n" +
               "                    splitPane.setDividerLocation(1.0);\n" +
               "                }\n" +
               "                else\n" +
               "                {\n" +
               "                    splitPane.setDividerLocation(0.7);\n" +
               "                }" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        if (!rightPanel.isAncestorOf(validationPanel)) {\n" +
     "            splitPane.setDividerLocation(1.0);\n" +
     "        }\n" +
     "        else {\n" +
     "            splitPane.setDividerLocation(0.7);\n" +
     "        }\n" +
     "    }\n" +
     "}");
  }

  public void testIDEADEV1047() {
    doTextTest("class Foo{\n" + "String field1\n" + ",\n" + "field2\n" + ";" + "}",
               "class Foo {\n" + "    String field1,\n" + "            field2;\n" + "}");

    doTextTest("class Foo{\n" + "void foo() {\n" + "    String var1\n" + ",\n" + "var2\n" + ";\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        String var1,\n" + "                var2;\n" + "    }\n" + "}");

  }

  public void testIDEADEV1047_2() {
    doTextTest(
      "class Foo{\n" +
      "String field1\n" +
      ",\n" +
      "field2\n" +
      "; String field3;}",
      "class Foo {\n" +
      "    String field1,\n" +
      "            field2;\n" +
      "    String field3;\n" +
      "}"
    );
  }

  public void testSCR2241() {
    getSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED;
    getSettings().SPECIAL_ELSE_IF_TREATMENT = true;
    getSettings().ELSE_ON_NEW_LINE = true;
    doTextTest("class Foo {\n" +
               "    void foo() {\n" +
               "        if (a)\n" +
               "        {\n" +
               "        }\n" +
               "        else\n" +
               "        {\n" +
               "        }\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        if (a)\n" +
     "            {\n" +
     "            }\n" +
     "        else\n" +
     "            {\n" +
     "            }\n" +
     "    }\n" +
     "}");
  }

    public void testSCRIDEA_4783() throws IncorrectOperationException {
    getSettings().ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().RIGHT_MARGIN = 80;

    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        final CommandRouterProtocolHandler protocolHandler =\n" +
     "                (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
     "    }\n" +
     "}");


    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        protocolHandlerCommandRouterProtocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        protocolHandlerCommandRouterProtocolHandler =\n" +
     "                (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
     "    }\n" +
     "}");

    doTextTest("class Foo{\n" +
               "    final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
"}", "class Foo {\n" +
     "    final CommandRouterProtocolHandler protocolHandler =\n" +
     "            (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
     "}");

    getSettings().PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = true;

    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        final CommandRouterProtocolHandler protocolHandler\n" +
     "                = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
     "    }\n" +
     "}");

    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        protocolHandlerCommandRouterProtocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
"}", "class Foo {\n" +
     "    void foo() {\n" +
     "        protocolHandlerCommandRouterProtocolHandler\n" +
     "                = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
     "    }\n" +
     "}");


    doTextTest("class Foo{\n" +
               "    final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
"}", "class Foo {\n" +
     "    final CommandRouterProtocolHandler protocolHandler\n" +
     "            = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
     "}");

  }

  public void testSCRIDEADEV_2292() throws IncorrectOperationException {
    getSettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false;
    getSettings().WHILE_ON_NEW_LINE = true;

    final JavaPsiFacade facade = getJavaFacade();
    final LanguageLevel stored = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);

    try {
      doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        if (a)\n" +
                 "            foo();\n" +
                 "        else\n" +
                 "            bar();\n" +
                 "    }\n" +
                 "}");


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        for (int i = 0; i < 10; i++)\n" +
                 "            foo();\n" +
                 "    }\n" +
                 "}");


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars)\n" + "            foo();\n" + "    }\n" + "}");


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                 "    void foo() {\n" +
                                                                                                                 "        do\n" +
                                                                                                                 "            foo();\n" +
                                                                                                                 "        while (true);\n" +
                                                                                                                 "    }\n" +
                                                                                                                 "}");


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true)\n" + "            foo();\n" + "    }\n" + "}");

      getSettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;
      getSettings().WHILE_ON_NEW_LINE = false;

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}");

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}");


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}");


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}");


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true) foo();\n" + "    }\n" + "}");

      getSettings().RIGHT_MARGIN = 17;

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        if (a)\n" +
                 "            foo();\n" +
                 "        else\n" +
                 "            bar();\n" +
                 "    }\n" +
                 "}");

      getSettings().RIGHT_MARGIN = 30;

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        for (int i = 0; i < 10; i++)\n" +
                 "            foo();\n" +
                 "    }\n" +
                 "}");

      getSettings().RIGHT_MARGIN = 32;
      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars)\n" + "            foo();\n" + "    }\n" + "}");


      getSettings().RIGHT_MARGIN = 12;
      doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                 "    void foo() {\n" +
                                                                                                                 "        do\n" +
                                                                                                                 "            foo();\n" +
                                                                                                                 "        while (true);\n" +
                                                                                                                 "    }\n" +
                                                                                                                 "}");

      getSettings().RIGHT_MARGIN = 23;

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true)\n" + "            foo();\n" + "    }\n" + "}");

    }
    finally {
      LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(stored);
    }


  }

  public void testSCR3115() {
    final CommonCodeStyleSettings.IndentOptions indentOptions = getSettings().getRootSettings().getIndentOptions(StdFileTypes.JAVA);
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.SMART_TABS = true;

    getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doTextTest("class Foo {\n" +
               "\tpublic void test(String[] args) {\n" +
               "\t\tfoo(new String[] {\n" +
               "\t\t\t\t\"1\",\n" +
               "\t\t        \"2\",\n" +
               "\t\t        \"3\"});\n" +
               "\t}\n" +
"}", "class Foo {\n" +
     "\tpublic void test(String[] args) {\n" +
     "\t\tfoo(new String[]{\n" +
     "\t\t\t\t\"1\",\n" +
     "\t\t\t\t\"2\",\n" +
     "\t\t\t\t\"3\"});\n" +
     "\t}\n" +
     "}");
  }

  public void testIDEADEV_6239() {
    getJavaSettings().ENABLE_JAVADOC_FORMATTING = true;
    doTextTest("public class Test {\n" +
               "\n" +
               "    /**\n" +
               "     * The s property.\n" +
               "     *\n" +
               "     * @deprecated don't use it\n" +
               "     */\n" +
               "    private String s;\n" +
"}", "public class Test {\n" +
     "\n" +
     "    /**\n" +
     "     * The s property.\n" +
     "     *\n" +
     "     * @deprecated don't use it\n" +
     "     */\n" +
     "    private String s;\n" +
     "}");
  }

  public void testIDEADEV_8755() throws IncorrectOperationException {
    getSettings().KEEP_LINE_BREAKS = false;
    doTextTest("class Foo {\n" +
               "void foo(){\n" +
               "System\n" +
               ".out\n" +
               ".println(\"Sleeping \" \n" +
               "+ thinkAboutItTime\n" +
               "+ \" seconds !\");" +
               "}\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        System.out.println(\"Sleeping \" + thinkAboutItTime + \" seconds !\");\n" +
                    "    }\n" +
                    "}");
  }

  public void testIDEADEV_24168() throws IncorrectOperationException {
    doTextTest(
      "class Foo {\n" + "@AnExampleMethod\n" + "public String\n" + "getMeAString()\n" + "throws AnException\n" + "{\n" + "\n" + "}\n" + "}",
      "class Foo {\n" +
      "    @AnExampleMethod\n" +
      "    public String\n" +
      "    getMeAString()\n" +
      "            throws AnException {\n" +
      "\n" +
      "    }\n" +
      "}");
  }


  public void testIDEADEV_2541() throws IncorrectOperationException {
    myTextRange = new TextRange(0, 15);
    doTextTest("/** @param q */\nclass Foo {\n}", "/**\n" + " * @param q\n" + " */\n" + "class Foo {\n" + "}");
  }

  public void testIDEADEV_6434() throws IncorrectOperationException {
    getSettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    getSettings().ALIGN_MULTILINE_ASSIGNMENT = true;
    doTextTest("class Foo {\n" +
               "void foo(){\n" +
               "return (interval1.getEndIndex() >= interval2.getStartIndex() &&\n" +
               "        interval1.getStartIndex() <= interval2.getEndIndex()) ||\n" +
               "                                                              (interval2.getEndIndex() >= interval1.getStartIndex() &&\n" +
               "                                                               interval2.getStartIndex() <= interval1.getEndIndex());\n" +
               "}\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        return (interval1.getEndIndex() >= interval2.getStartIndex() &&\n" +
                    "                interval1.getStartIndex() <= interval2.getEndIndex()) ||\n" +
                    "               (interval2.getEndIndex() >= interval1.getStartIndex() &&\n" +
                    "                interval2.getStartIndex() <= interval1.getEndIndex());\n" +
                    "    }\n" +
                    "}");
  }

  public void testIDEADEV_12836() throws IncorrectOperationException {
    getSettings().SPECIAL_ELSE_IF_TREATMENT = true;
    getSettings().RIGHT_MARGIN = 80;
    doTextTest("class Foo {\n" +
               "void foo(){\n" +
               "if (true){\n" +
               "} else if (\"                                                            \" != null) {\n" +
               "}\n" +
               "}\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        if (true) {\n" +
                    "        } else if (\"                                                            \" != null) {\n" +
                    "        }\n" +
                    "    }\n" +
                    "}");

  }
  /*
  public void testIDEADEV_26871() throws IncorrectOperationException {
    getSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 4;
    doTextTest("class Foo {\n" +
               "public void foo() {\n" +
               "    BigDecimal1.ONE1\n" +
               "    .add2(BigDecimal2.ONE2\n" +
               "    .add3(BigDecimal3.ONE3\n" +
               "    .add4(BigDecimal4.ONE4\n" +
               "    .add5(BigDecimal5.ONE5))))\n" +
               "}\n" +
               "}",
               "class Foo {\n" +
               "    public void foo() {\n" +
               "        BigDecimal1.ONE1\n" +
               "            .add2(BigDecimal2.ONE2\n" +
               "                .add3(BigDecimal3.ONE3\n" +
               "                    .add4(BigDecimal4.ONE4\n" +
               "                        .add5(BigDecimal5.ONE5))))\n" +
               "    }\n" +
               "}");
  }
  */

  public void test23551() throws IncorrectOperationException {
    doTextTest("public class Wrapping {\n" +
               "    public static void sample() {\n" +
               "        System.out.println(\".\" + File.separator + \"..\" + File.separator + \"some-directory-name\" + File.separator + \"more-file-name\");\n" +
               "    }\n" +
               "}", "public class Wrapping {\n" +
                    "    public static void sample() {\n" +
                    "        System.out.println(\".\" + File.separator + \"..\" + File.separator + \"some-directory-name\" + File.separator + \"more-file-name\");\n" +
                    "    }\n" +
                    "}");
  }

  /*
  public void testIDEADEV_26871_2() throws IncorrectOperationException {
    getSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 4;
    doTextTest("class Foo {\n" +
               "public void foo() {\n" +
               "    BigDecimal1\n" +
               "    .add2(BigDecimal2\n" +
               "    .add3(BigDecimal3\n" +
               "    .add4(BigDecimal4\n" +
               "    .add5(BigDecimal5))))\n" +
               "}\n" +
               "}",
               "class Foo {\n" +
               "    public void foo() {\n" +
               "        BigDecimal1.ONE1\n" +
               "            .add2(BigDecimal2.ONE2\n" +
               "                .add3(BigDecimal3.ONE3\n" +
               "                    .add4(BigDecimal4.ONE4\n" +
               "                        .add5(BigDecimal5.ONE5))))\n" +
               "    }\n" +
               "}");
  }

  */
  public void testIDEADEV_23551() throws IncorrectOperationException {
    getSettings().BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

    getSettings().RIGHT_MARGIN = 60;
    doTextTest("public class Wrapping {\n" +
               "public static void sample() {\n" +
               "System.out.println(\".\" + File.separator + \"..\" + File.separator + \"some-directory-name\" + File.separator + \"more-file-name\");\n" +
               "}\n" +
               "}", "public class Wrapping {\n" +
                    "    public static void sample() {\n" +
                    "        System.out.println(\".\" +\n" +
                    "                File.separator +\n" +
                    "                \"..\" +\n" +
                    "                File.separator +\n" +
                    "                \"some-directory-name\" +\n" +
                    "                File.separator +\n" +
                    "                \"more-file-name\");\n" +
                    "    }\n" +
                    "}");
  }

  public void testIDEADEV_22967() throws IncorrectOperationException {
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doTextTest("public interface TestInterface {\n" +
               "\n" +
               "    void empty();\n" +
               "\n" +
               "    @Deprecated\n" +
               "    void annotated();\n" +
               "\n" +
               "    <T> void parametrized(T data);\n" +
               "\n" +
               "    @Deprecated\n" +
               "    <T> void parametrizedAnnotated(T data);\n" +
               "\n" +
               "    @Deprecated\n" +
               "    public <T> void publicParametrizedAnnotated(T data);\n" +
               "\n" +
               "}", "public interface TestInterface {\n" +
                    "\n" +
                    "    void empty();\n" +
                    "\n" +
                    "    @Deprecated\n" +
                    "    void annotated();\n" +
                    "\n" +
                    "    <T> void parametrized(T data);\n" +
                    "\n" +
                    "    @Deprecated\n" +
                    "    <T> void parametrizedAnnotated(T data);\n" +
                    "\n" +
                    "    @Deprecated\n" +
                    "    public <T> void publicParametrizedAnnotated(T data);\n" +
                    "\n" +
                    "}");
  }

  public void testIDEADEV_22967_2() throws IncorrectOperationException {
    getSettings().METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    doTextTest("public interface TestInterface {\n" + "    @Deprecated\n" + "    <T> void parametrizedAnnotated(T data);\n" + "}",
               "public interface TestInterface {\n" + "    @Deprecated\n" + "    <T> void parametrizedAnnotated(T data);\n" + "}");
  }

  public void testIDEA_54406() {
    doTextTest("public class Test1 {\n" +
               "void func() {\n" +
               "new Thread(new Runnable() {\n" +
               "public void run() {\n" +
               " // ...\n" +
               "}\n" +
               "}\n" +
               ")\n" +
               ";\n" +
               "}\n" +
               "}",

               "public class Test1 {\n" +
               "    void func() {\n" +
               "        new Thread(new Runnable() {\n" +
               "            public void run() {\n" +
               "                // ...\n" +
               "            }\n" +
               "        }\n" +
               "        )\n" +
               "        ;\n" +
               "    }\n" +
               "}");
  }

  public void testCommaInMethodDeclParamsList() {
    getSettings().SPACE_AFTER_COMMA = true;
    String before = "public class Test {\n" +
                    "    public static void act(   int a   ,    int b   ,    int c   ,      int d) {\n" +
                    "        act(1,2,3,4);\n" +
                    "    }\n" +
                    "}\n";
    String after = "public class Test {\n" +
                   "    public static void act(int a, int b, int c, int d) {\n" +
                   "        act(1, 2, 3, 4);\n" +
                   "    }\n" +
                   "}\n";
    doTextTest(before, after);
    getSettings().SPACE_AFTER_COMMA = false;
    before = "public class Test {\n" +
             "    public static void act(   int a   ,    int b   ,      int c   ,      int d) {\n" +
             "        act(1 ,   2 , 3 ,            4);\n" +
             "    }\n" +
             "}\n";
    after = "public class Test {\n" +
            "    public static void act(int a,int b,int c,int d) {\n" +
            "        act(1,2,3,4);\n" +
            "    }\n" +
            "}\n";
    doTextTest(before, after);
  }



  public void testFormatterOnOffTags() {
    getSettings().getRootSettings().FORMATTER_TAGS_ENABLED = true;
    getSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    doTest();
  }

  public void testFormatterOnOffTagsWithRegexp() {
    CodeStyleSettings settings = getSettings().getRootSettings();
    settings.FORMATTER_TAGS_ENABLED = true;
    settings.FORMATTER_TAGS_ACCEPT_REGEXP = true;
    settings.FORMATTER_OFF_TAG = "not.*format";
    settings.FORMATTER_ON_TAG = "end.*fragment";
    doTest();
  }

  public void testDoNotIndentNotSelectedStatement_AfterSelectedOne() {
    myTextRange = new TextRange(0, 73);
    doTextTest(
      "class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "    int a = 3;\n" +
      "    System.out.println(\"AAA\");\n" +
      "    }\n" +
      "}",
      "class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        int a = 3;\n" +
      "    System.out.println(\"AAA\");\n" +
      "    }\n" +
      "}"
    );

    myTextRange = new TextRange(0, 67);
    doTextTest(
      "    import java.lang.Override;\n" +
      "    import java.lang.Exception;\n" +
      "    \n" +
      "    class Foo {\n" +
      "}",
      "import java.lang.Override;\n" +
      "import java.lang.Exception;\n" +
      "    \n" +
      "    class Foo {\n" +
      "}"
    );
  }

  public void testDetectableIndentOptions() {
    final String original =
       "public class Main {\n" +
      "\tpublic void main() {\n" +
      "try {\n" +
      "\t\t\tSystem.out.println();\n" +
      "\t} catch (java.lang.Exception exception) {\n" +
      "\t\t\texception.printStackTrace();\n" +
      "\t}\n" +
      "}\n" +
      "}";
    // Enabled but full reformat (no detection)
    doTestWithDetectableIndentOptions(
      original,
      "public class Main {\n" +
      "    public void main() {\n" +
      "        try {\n" +
      "            System.out.println();\n" +
      "        } catch (java.lang.Exception exception) {\n" +
      "            exception.printStackTrace();\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
    // Reformat with a smaller text range (detection is on)
    myTextRange = new TextRange(1, original.length());
    doTestWithDetectableIndentOptions(
      original,
      "public class Main {\n" +
      "\tpublic void main() {\n" +
      "\t\ttry {\n" +
      "\t\t\tSystem.out.println();\n" +
      "\t\t} catch (java.lang.Exception exception) {\n" +
      "\t\t\texception.printStackTrace();\n" +
      "\t\t}\n" +
      "\t}\n" +
      "}"
    );
  }

  public void testKeepIndentsOnEmptyLines() {
    CommonCodeStyleSettings.IndentOptions indentOptions = getSettings().getIndentOptions();
    assertNotNull(indentOptions);
    final String original =
         "public class Main {\n" +
      "    public int x;\n" +
      "           \n" +
      "    public int y;\n" +
      "\n" +
      "    public void foo(boolean a, int x, int y, int z) {\n" +
      "        do {\n" +
      "  \n" +
      "            if (x > 0) {\n" +
      "  \n" +
      "            } else if (x < 0) {\n" +
      "            \n" +
      "            int r;\n" +
      "            }\n" +
      "    }\n" +
      "        while (y > 0);\n" +
      "  }\n" +
      "}";

    indentOptions.KEEP_INDENTS_ON_EMPTY_LINES = false;
    doTextTest(
      original,

      "public class Main {\n" +
      "    public int x;\n" +
      "\n" +
      "    public int y;\n" +
      "\n" +
      "    public void foo(boolean a, int x, int y, int z) {\n" +
      "        do {\n" +
      "\n" +
      "            if (x > 0) {\n" +
      "\n" +
      "            } else if (x < 0) {\n" +
      "\n" +
      "                int r;\n" +
      "            }\n" +
      "        }\n" +
      "        while (y > 0);\n" +
      "    }\n" +
      "}"
    );
    indentOptions.KEEP_INDENTS_ON_EMPTY_LINES = true;
    doTextTest(
      original,

      "public class Main {\n" +
      "    public int x;\n" +
      "    \n" +
      "    public int y;\n" +
      "    \n" +
      "    public void foo(boolean a, int x, int y, int z) {\n" +
      "        do {\n" +
      "            \n" +
      "            if (x > 0) {\n" +
      "                \n" +
      "            } else if (x < 0) {\n" +
      "                \n" +
      "                int r;\n" +
      "            }\n" +
      "        }\n" +
      "        while (y > 0);\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea114862() {
    getSettings().getRootSettings().FORMATTER_TAGS_ENABLED = true;
    CommonCodeStyleSettings.IndentOptions indentOptions = getSettings().getIndentOptions();
    assertNotNull(indentOptions);
    indentOptions.USE_TAB_CHARACTER = true;
    doTextTest(
      "// @formatter:off \n" +
      "public class Test {\n" +
      "      String foo;\n" +
      "      String bar;\n" +
      "}",

      "// @formatter:off \n" +
      "public class Test {\n" +
      "      String foo;\n" +
      "      String bar;\n" +
      "}"
    );
  }

  public void testReformatCodeWithErrorElementsWithoutAssertions() {
    doTextTest("class  RedTest   {   \n\n\n\n\n\n\n\n   " +
               "String  [  ]  [  ]   test    =    {       { \n\n\n\n\n {    \"\"}  \n\n\n\n\n };   " +
               "String  [  ]  [  ]   test    =    {       { \n\n\n\n\n {    \"\"}  \n\n\n\n\n };   " +
               "                      \n\n\n\n\n\n\n\n  }  ",
               "class RedTest {\n\n\n" +
               "    String[][] test = {{\n\n\n" +
               "            {\"\"}\n\n\n" +
               "    };\n" +
               "    String[][] test = {{\n\n\n" +
               "            {\"\"}\n\n\n" +
               "    };\n\n\n" +
               "}  ");
  }
  
  public void testReformatPackageAnnotation() {
    doTextTest(
      "@ParametersAreNonnullByDefault package com.example;",
      "@ParametersAreNonnullByDefault\n" +
      "package com.example;"
    );
    
    doTextTest(
      "        @ParametersAreNonnullByDefault\n" +
      "package com.example;",
      "@ParametersAreNonnullByDefault\n" +
      "package com.example;"
    );
  }

  public void testFinal_OnTheEndOfLine() {
    doMethodTest(
      "@SuppressWarnings(\"unchecked\") final\n" +
      "List<String> list = new ArrayList<String>();\n" +
      "new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "        list.clear();\n" +
      "    }\n" +
      "};",
      "@SuppressWarnings(\"unchecked\") final List<String> list = new ArrayList<String>();\n" +
      "new Runnable() {\n" +
      "    @Override\n" +
      "    public void run() {\n" +
      "        list.clear();\n" +
      "    }\n" +
      "};"
    );
  }

  public void testKeepTypeAnnotationNearType() {
    doTextTest(
      "import java.lang.annotation.ElementType;\n" +
      "import java.lang.annotation.Retention;\n" +
      "import java.lang.annotation.RetentionPolicy;\n" +
      "import java.lang.annotation.Target;\n" +
      "\n" +
      "@Target(value=ElementType.TYPE_USE)\n" +
      "@Retention(value= RetentionPolicy.RUNTIME)\n" +
      "public @interface X {}\n" +
      "class Q {\n" +
      "  @Override\n" +
      "  public @X List<Object> objects() {\n" +
      "    return null;\n" +
      "  }\n" +
      "}",
      
      "import java.lang.annotation.ElementType;\n" +
      "import java.lang.annotation.Retention;\n" +
      "import java.lang.annotation.RetentionPolicy;\n" +
      "import java.lang.annotation.Target;\n" +
      "\n" +
      "@Target(value = ElementType.TYPE_USE)\n" +
      "@Retention(value = RetentionPolicy.RUNTIME)\n" +
      "public @interface X {\n" +
      "}\n" +
      "\n" +
      "class Q {\n" +
      "    @Override\n" +
      "    public @X List<Object> objects() {\n" +
      "        return null;\n" +
      "    }\n" +
      "}"
    );
  }
  
  public void testKeepSimpleSwitchInOneLine() {
    getSettings().CASE_STATEMENT_ON_NEW_LINE = false;
    doMethodTest(
      "switch (b) {\n" +
      "case 1: case 2: break;\n" +
      "}",
      "switch (b) {\n" +
      "    case 1: case 2: break;\n" +
      "}");
  }
  
  public void testExpandSwitch() {
    getSettings().CASE_STATEMENT_ON_NEW_LINE = false;
    doMethodTest(
      "switch (b) {\n" +
      "case 1: { println(1); } case 2: break;\n" +
      "}", 
      "switch (b) {\n" +
      "    case 1: {\n" +
      "        println(1);\n" +
      "    }\n" +
      "    case 2: break;\n" +
      "}");
  }

  public void testKeepBreakOnSameLine() {
    getSettings().CASE_STATEMENT_ON_NEW_LINE = false;
    doMethodTest(
      "switch (b) {\n" +
      "case 1: case 2:\n" +
      "\n\n\n\n\n\n" +
      "break;\n" +
      "}",
      "switch (b) {\n" +
      "    case 1: case 2:\n\n\n" +
      "        break;\n" +
      "}");
  }

  public void testAnnotationAndFinalInsideParamList() {
    doClassTest(
      "public class Test {\n" +
      "    void test(@Nullable    final String childFallbackProvider) {\n" +
      "    }\n" +
      "}",
      "public class Test {\n" +
      "    void test(@Nullable final String childFallbackProvider) {\n" +
      "    }\n" +
      "}"
    );
  }

  public void testPreserveRbraceOnItsLine() {
    doClassTest(
      "class SomeTest {\n" +
      "  @Test\n" +
      "}",
      "class SomeTest {\n" +
      "    @Test\n" +
      "}"
    );
  }

  public void testFormatCStyleCommentWithAsterisks() {
    doMethodTest(
      "        for (Object o : new Object[]{}) {\n" +
      "/*\n" +
      "        *\n" +
      " \t\t\t\t\t            */\n" +
      "        }\n",
      "for (Object o : new Object[]{}) {\n" +
      "    /*\n" +
      "     *\n" +
      "     */\n" +
      "}\n"
    );
  }
  
}
