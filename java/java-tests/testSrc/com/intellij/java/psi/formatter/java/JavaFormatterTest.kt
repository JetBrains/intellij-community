// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java

import com.intellij.application.options.CodeStyle
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaCodeFragmentFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.util.IncorrectOperationException

/**
 * **Note:** this class is too huge and hard to use. It's tests are intended to be split in multiple more fine-grained
 * java formatting test classes.
 */
class JavaFormatterTest : AbstractJavaFormatterTest() {
  
  fun testPaymentManager() {
    settings.KEEP_LINE_BREAKS = false
    doTest("paymentManager.java", "paymentManager_after.java")
  }

  fun testForEach() {
    doTest("ForEach.java", "ForEach_after.java")
  }

  fun testDoubleCast() {
    doTest("DoubleCast.java", "DoubleCast_after.java")
  }

  fun test1() {
    myTextRange = TextRange(35, 46)
    doTest("1.java", "1_after.java")
  }

  fun testLabel1() {
    indentOptions.LABEL_INDENT_SIZE = 0
    indentOptions.LABEL_INDENT_ABSOLUTE = true
    doTest("Label.java", "Label_after1.java")
  }

  fun testTryCatch() {
    myTextRange = TextRange(38, 72)
    doTest("TryCatch.java", "TryCatch_after.java")
  }

  fun testNullMethodParameter() {
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest("NullMethodParameter.java", "NullMethodParameter_after.java")
  }

  fun test_DoNot_JoinLines_If_KeepLineBreaksIsOn() {
    settings.KEEP_LINE_BREAKS = true
    settings.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
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
    )
  }

  fun test_DoNot_JoinLines_If_KeepLineBreaksIsOn_WithMultipleAnnotations() {
    settings.KEEP_LINE_BREAKS = true
    settings.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
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
    )
  }

  fun test_format_only_selected_range() {
    myTextRange = TextRange(18, 19)
    doTextTest(
      "public class X {\n" +
      " public int a =       2;\n" +
      "}",
      "public class X {\n" +
      "    public int a =       2;\n" +
      "}"
    )
  }

  fun testNew() {
    indentOptions.CONTINUATION_INDENT_SIZE = 8
    doTest("New.java", "New_after.java")
  }

  fun testJavaDoc() {
    settings.BLANK_LINES_AROUND_FIELD = 1
    doTest("JavaDoc.java", "JavaDoc_after.java")
  }

  fun testBreakInsideIf() {
    doTest("BreakInsideIf.java", "BreakInsideIf_after.java")
  }

  fun testAssert() {
    IdeaTestUtil.setProjectLanguageLevel(project, LanguageLevel.HIGHEST)
    doTest()
  }

  fun testCastInsideElse() {
    indentOptions.CONTINUATION_INDENT_SIZE = 2
    indentOptions.INDENT_SIZE = 2
    indentOptions.LABEL_INDENT_SIZE = 0
    indentOptions.TAB_SIZE = 8
    settings.SPACE_WITHIN_CAST_PARENTHESES = false
    settings.SPACE_AFTER_TYPE_CAST = true
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true
    doTest()
  }

  fun testAlignMultiLine() {
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testInnerClassAsParameter() {
    doTest()
  }

  fun testSynchronizedBlock() {
    settings.apply {
      SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false
      SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = false
      SPACE_BEFORE_SYNCHRONIZED_LBRACE = false
    }
    doTest()
  }

  fun testMethodCallInAssignment() {
    val settings = settings
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 8
    doTest()
  }

  fun testAnonymousInnerClasses() {
    doTest()
  }

  fun testAnonymousInner2() {
    doTest()
  }

  fun testWrapAssertion() {
    doTest()
  }

  fun testIfElse() {
    settings.apply {
      IF_BRACE_FORCE = CommonCodeStyleSettings.DO_NOT_FORCE
      FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE
      WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE
      DOWHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE

      ELSE_ON_NEW_LINE = true
      SPECIAL_ELSE_IF_TREATMENT = false
      WHILE_ON_NEW_LINE = true
      CATCH_ON_NEW_LINE = true
      FINALLY_ON_NEW_LINE = true
      ALIGN_MULTILINE_BINARY_OPERATION = true
      ALIGN_MULTILINE_TERNARY_OPERATION = true
      ALIGN_MULTILINE_ASSIGNMENT = true
      ALIGN_MULTILINE_EXTENDS_LIST = true
      ALIGN_MULTILINE_THROWS_LIST = true
      ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true
      ALIGN_MULTILINE_FOR = true
      ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
      ALIGN_MULTILINE_PARAMETERS = true
      KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
      WHILE_ON_NEW_LINE = true
      BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE

      SPACE_WITHIN_BRACES = true
    }
    doTest()
  }

  fun testIfBraces() {
    settings.apply {
      IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
      BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
      KEEP_LINE_BREAKS = false
    }
    doTest()
  }

  fun testTernaryExpression() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()

    settings.ALIGN_MULTILINE_TERNARY_OPERATION = false
    doTest("TernaryExpression.java", "TernaryExpression_DoNotAlign_after.java")

  }

  fun testAlignAssignment() {
    settings.apply {
      ALIGN_MULTILINE_ASSIGNMENT = true
      ALIGN_MULTILINE_BINARY_OPERATION = true
    }
    doTest()
  }

  fun testAlignFor() {
    settings.apply {
      ALIGN_MULTILINE_BINARY_OPERATION = true
      ALIGN_MULTILINE_FOR = true
    }
    doTest()
  }

  fun testSwitch() {
    doTest()
  }

  fun testContinue() {
    doTest()
  }

  fun testIf() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    doTest("If.java", "If.java")
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    settings.KEEP_LINE_BREAKS = false
    doTest("If_after.java", "If.java")

  }

  fun test2() {
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest()
  }

  fun testBlocks() {
    settings.KEEP_LINE_BREAKS = false
    doTest()
  }

  @Throws(IncorrectOperationException::class)
  fun testBinaryOperation() {
    val text = "class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        + ccc + eee + ddd;\n" + "    }\n" + "}"

    settings.apply {
      ALIGN_MULTILINE_BINARY_OPERATION = true
      ALIGN_MULTILINE_ASSIGNMENT = true
    }
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "              + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")
    settings.apply {
      ALIGN_MULTILINE_BINARY_OPERATION = true
      ALIGN_MULTILINE_ASSIGNMENT = false
    }
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "              + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")

    settings.apply {
      ALIGN_MULTILINE_BINARY_OPERATION = false
      ALIGN_MULTILINE_ASSIGNMENT = true
    }
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")

    settings.apply {
      ALIGN_MULTILINE_ASSIGNMENT = false
      ALIGN_MULTILINE_BINARY_OPERATION = false
    }

    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")

    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")


    settings.ALIGN_MULTILINE_BINARY_OPERATION = true

    doTextTest("class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        - ccc + eee + ddd;\n" + "    }\n" + "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        xxx = aaa + bbb\n" +
               "              - ccc + eee + ddd;\n" +
               "    }\n" +
               "}")

    doTextTest("class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        * ccc + eee + ddd;\n" + "    }\n" + "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        xxx = aaa + bbb\n" +
               "                    * ccc + eee + ddd;\n" +
               "    }\n" +
               "}")

  }

  fun testWhile() {
    doTextTest("class A{\n" + "void a(){\n" + "do x++ while (b);\n" + "}\n}",
               "class A {\n" + "    void a() {\n" + "        do x++ while (b);\n" + "    }\n" + "}")
  }

  fun testFor() {
    doTextTest("class A{\n" + "void b(){\n" + "for (c) {\n" + "d();\n" + "}\n" + "}\n" + "}",
               "class A {\n" + "    void b() {\n" + "        for (c) {\n" + "            d();\n" + "        }\n" + "    }\n" + "}")
  }

  fun testClassComment() {
    val before = "/**\n" +
                 "* @author smbd\n" +
                 "* @param <T> some param\n" +
                 "* @since 1.9\n" +
                 "*/\n" +
                 "class Test<T>{}"
    val after = "/**\n" +
                " * @param <T> some param\n" +
                " * @author smbd\n" +
                " * @since 1.9\n" +
                " */\n" +
                "class Test<T> {\n" +
                "}"
    doTextTest(before, after)
  }

  fun testStringBinaryOperation() {
    settings.apply {
      ALIGN_MULTILINE_ASSIGNMENT = false
      ALIGN_MULTILINE_BINARY_OPERATION = false
    }

    doTextTest("class Foo {\n" + "    void foo () {\n" + "String s = \"abc\" +\n" + "\"def\";" + "    }\n" + "}",

               "class Foo {\n" +
               "    void foo() {\n" +
               "        String s = \"abc\" +\n" +
               "                \"def\";\n" +
               "    }\n" +
               "}")

  }

  fun test3() {
    doTest()
  }

  fun test4() {
    myLineRange = TextRange(2, 8)
    doTest()
  }

  fun testBraces() {

    val text = "class Foo {\n" +
                       "void foo () {\n" +
                       "if (a) {\n" +
                       "int i = 0;\n" +
                       "}\n" +
                       "}\n" +
                       "}"

    settings.apply {
      BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    }
    doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        if (a) {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "    }\n" +
                     "}")

    settings.apply {
      BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    }
    doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "    {\n" +
                     "        if (a)\n" +
                     "        {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "    }\n" +
                     "}")

    settings.apply {
      BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    }
    doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "        if (a)\n" +
                     "            {\n" +
                     "            int i = 0;\n" +
                     "            }\n" +
                     "        }\n" +
                     "}")
    settings.apply {
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
      BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    }
    doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "        if (a) {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "        }\n" +
                     "}")

    settings.apply {
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
      BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    }
    doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "            if (a)\n" +
                     "                {\n" +
                     "                    int i = 0;\n" +
                     "                }\n" +
                     "        }\n" +
                     "}")

    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTextTest("class Foo {\n" + "    static{\n" + "foo();\n" + "}" + "}",
               "class Foo {\n" + "    static\n" + "    {\n" + "        foo();\n" + "    }\n" + "}")

  }

  fun testExtendsList() {
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true
    doTextTest("class A extends B, \n" + "C {}", "class A extends B,\n" + "                C {\n}")
  }

  fun testBlockWithoutBraces() {
    doTextTest("class A {\n" + "void foo(){\n" + "if(a)\n" + "return;\n" + "}\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        if (a)\n" + "            return;\n" + "    }\n" + "}")
  }

  fun testNestedCalls() {
    doTextTest("class A {\n" + "void foo(){\n" + "foo(\nfoo(\nfoo()\n)\n);\n" + "}\n" + "}", "class A {\n" +
                                                                                             "    void foo() {\n" +
                                                                                             "        foo(\n" +
                                                                                             "                foo(\n" +
                                                                                             "                        foo()\n" +
                                                                                             "                )\n" +
                                                                                             "        );\n" +
                                                                                             "    }\n" +
                                                                                             "}")

  }

  fun testSpacesAroundMethod() {
    doTextTest("class Foo {\n" + "    abstract void a();\n" + "    {\n" + "        a();\n" + "    }\n" + "}",
               "class Foo {\n" + "    abstract void a();\n" + "\n" + "    {\n" + "        a();\n" + "    }\n" + "}")
  }

  fun testSpaceInIf() {
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
                    "}")
  }

  fun testIf2() {
    doTextTest(
      "public class Test {\n" + "    public boolean equals(Object o) {\n" + "        if(this == o)return true;\n" + "    }\n" + "}",
      "public class Test {\n" + "    public boolean equals(Object o) {\n" + "        if (this == o) return true;\n" + "    }\n" + "}")
  }

  fun testSpaceAroundField() {
    settings.BLANK_LINES_AROUND_FIELD = 1

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
                    "}")
  }

  fun testArray() {
    settings.apply {
      SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = true
      SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true
    }
    doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{0};\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[] { 0 };\n" + "    }\n" + "}")
  }

  fun testEmptyArray() {
    settings.apply {
      SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = true
      SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true
      SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = false
    }
    doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{ };\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[] {};\n" + "    }\n" + "}")
  }

  fun testEmptyArrayIsntWrapped() {
    settings.apply {
      ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true
      ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true
      SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = true
    }
    doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{ };\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[]{ };\n" + "    }\n" + "}")
  }

  fun testTwoJavaDocs() {
    doTextTest("""/**
 *
 */
        class Test {
    /**
     */
     public void foo();
}""",
               """/**
 *
 */
class Test {
    /**
     *
     */
    public void foo();
}""")
  }

  fun testJavaDocLinksWithParameterNames() {
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
                      "}\n")
  }

  fun testIncompleteField() {
    doTextTest("public class Test {\n" + "    String s =;\n" + "}", "public class Test {\n" + "    String s = ;\n" + "}")
  }

  fun testIf3() {
    settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
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
                    "}")
  }

  fun testDocComment() {
    doTextTest("public class TestClass {\n" + "/**\n" + "* \n" + "*/\n" + "    public void f1() {\n" + "    }\n" + "}",
               "public class TestClass {\n" + "    /**\n" + "     *\n" + "     */\n" + "    public void f1() {\n" + "    }\n" + "}")
  }

  fun testDocComment2() {
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    javaSettings.JD_KEEP_EMPTY_LINES = false
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
                    "}")
  }

  fun testSpaceBeforeFieldName() {
    doTextTest("class A{\n" + "public   A    myA ;\n" + "}", "class A {\n" + "    public A myA;\n" + "}")
  }

  fun testClass() {
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
                        "}")
  }

  fun testDoNotIndentCaseFromSwitch() {
    settings.INDENT_CASE_FROM_SWITCH = false
    doTextTest("class A {\n" + "void foo() {\n" + "switch(a){\n" + "case 1: \n" + "break;\n" + "}\n" + "}\n" + "}", "class A {\n" +
                                                                                                                    "    void foo() {\n" +
                                                                                                                    "        switch (a) {\n" +
                                                                                                                    "        case 1:\n" +
                                                                                                                    "            break;\n" +
                                                                                                                    "        }\n" +
                                                                                                                    "    }\n" +
                                                                                                                    "}")
  }

  fun testClass2() {
    settings.KEEP_FIRST_COLUMN_COMMENT = false
    doTextTest("class A {\n" + "// comment before\n" + "protected Object a;//  comment after\n" + "}",
               "class A {\n" + "    // comment before\n" + "    protected Object a;//  comment after\n" + "}")
  }

  fun testSplitLiteral() {
    doTextTest("class A {\n" + "void foo() {\n" + "  String s = \"abc\" +\n" + "  \"def\";\n" + "}\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        String s = \"abc\" +\n" + "                \"def\";\n" + "    }\n" + "}")
  }

  fun testParametersAlignment() {
    settings.apply {
      ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
      RIGHT_MARGIN = 140
    }
    doTest()
  }

  fun testConditionalExpression() {
    settings.apply {
      SPACE_BEFORE_QUEST = true
      SPACE_AFTER_QUEST = false
      SPACE_BEFORE_COLON = true
      SPACE_AFTER_COLON = false
    }

    doTextTest("class Foo{\n" + "  void foo(){\n" + "  return name   !=   null   ?   1   :   2   ;" + "}\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        return name != null ?1 :2;\n" + "    }\n" + "}")
  }

  fun testMethodCallChain() {
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
                    "}")

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
                    "}")

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
                    "}")
  }

  fun testComment1() {
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
      "}")
  }

  fun testElseAfterComment() {
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
                    "}")
  }

  fun testLBraceAfterComment() {
    settings.KEEP_LINE_BREAKS = false
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
                    "}")
  }

  fun testSpaces() {
    val settings = settings
    settings.SPACE_WITHIN_FOR_PARENTHESES = true
    settings.SPACE_WITHIN_IF_PARENTHESES = true
    settings.SPACE_WITHIN_METHOD_PARENTHESES = true
    settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    settings.SPACE_BEFORE_METHOD_PARENTHESES = true
    settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    doTest()
  }

  fun testSpacesBeforeLBrace() {
    val settings = settings
    settings.SPACE_BEFORE_CLASS_LBRACE = true
    settings.SPACE_BEFORE_METHOD_LBRACE = true
    settings.SPACE_BEFORE_IF_LBRACE = true
    settings.SPACE_BEFORE_ELSE_LBRACE = true
    settings.SPACE_BEFORE_WHILE_LBRACE = true
    settings.SPACE_BEFORE_FOR_LBRACE = true
    settings.SPACE_BEFORE_DO_LBRACE = true
    settings.SPACE_BEFORE_SWITCH_LBRACE = true
    settings.SPACE_BEFORE_TRY_LBRACE = true
    settings.SPACE_BEFORE_CATCH_LBRACE = true
    settings.SPACE_BEFORE_FINALLY_LBRACE = true
    settings.SPACE_BEFORE_SYNCHRONIZED_LBRACE = true
    settings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true

    doTest()

    settings.SPACE_BEFORE_CLASS_LBRACE = false
    settings.SPACE_BEFORE_METHOD_LBRACE = false
    settings.SPACE_BEFORE_IF_LBRACE = false
    settings.SPACE_BEFORE_ELSE_LBRACE = false
    settings.SPACE_BEFORE_WHILE_LBRACE = false
    settings.SPACE_BEFORE_FOR_LBRACE = false
    settings.SPACE_BEFORE_DO_LBRACE = false
    settings.SPACE_BEFORE_SWITCH_LBRACE = false
    settings.SPACE_BEFORE_TRY_LBRACE = false
    settings.SPACE_BEFORE_CATCH_LBRACE = false
    settings.SPACE_BEFORE_FINALLY_LBRACE = false
    settings.SPACE_BEFORE_SYNCHRONIZED_LBRACE = false
    settings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false

    doTest("SpacesBeforeLBrace.java", "SpacesBeforeLBrace.java")
  }

  fun testCommentBeforeField() {
    val settings = settings
    settings.KEEP_LINE_BREAKS = false
    settings.KEEP_FIRST_COLUMN_COMMENT = false
    settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false
    doTextTest("class Foo{\n" + "    //Foo a\n" + "    Foo a; \n" + "}", "class Foo {\n" + "    //Foo a\n" + "    Foo a;\n" + "}")
  }

  fun testLabel() {
    val settings = settings
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).LABEL_INDENT_ABSOLUTE = true
    settings.SPECIAL_ELSE_IF_TREATMENT = true
    settings.FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    myTextRange = TextRange(59, 121)
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
                    "}")
  }

  fun testElseOnNewLine() {
    doTextTest("class Foo{\n" + "void foo() {\n" + "if (a)\n" + "return;\n" + "else\n" + "return;\n" + "}\n" + "}", "class Foo {\n" +
                                                                                                                    "    void foo() {\n" +
                                                                                                                    "        if (a)\n" +
                                                                                                                    "            return;\n" +
                                                                                                                    "        else\n" +
                                                                                                                    "            return;\n" +
                                                                                                                    "    }\n" +
                                                                                                                    "}")
  }

  fun testTwoClasses() {
    doTextTest("class A {}\n" + "class B {}", "class A {\n" + "}\n" + "\n" + "class B {\n" + "}")
  }

  fun testBraceOnNewLineIfWrapped() {
    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    settings.RIGHT_MARGIN = 35
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true

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
                    "}")
  }

  fun testFirstArgumentWrapping() {
    settings.RIGHT_MARGIN = 20
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(1);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(\n" + "                1);\n" + "    }\n" + "}")

    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(1,2);" + "    }\n" + "}", "class Foo {\n" +
                                                                                                        "    void foo() {\n" +
                                                                                                        "        fooFooFooFoo(\n" +
                                                                                                        "                1,\n" +
                                                                                                        "                2);\n" +
                                                                                                        "    }\n" +
                                                                                                        "}")

    doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFoo(1,2);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        fooFooFoo(1,\n" + "                2);\n" + "    }\n" + "}")

  }

  fun testSpacesInsideWhile() {
    settings.SPACE_WITHIN_WHILE_PARENTHESES = true
    doTextTest("class Foo{\n" + "    void foo() {\n" + "        while(x != y) {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        while ( x != y ) {\n" + "        }\n" + "    }\n" + "}")
  }

  fun testAssertStatementWrapping() {
    settings.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    settings.RIGHT_MARGIN = 40
    val facade = javaFacade
    val effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.project).languageLevel
    try {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)

      settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false
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
                        "}\n")

      settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = true
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
                        "}\n")

    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, effectiveLanguageLevel)
    }
  }

  fun testAssertStatementWrapping2() {
    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    settings.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.RIGHT_MARGIN = 37

    val options = settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE)
    options.INDENT_SIZE = 2
    options.CONTINUATION_INDENT_SIZE = 2

    settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = true

    val facade = javaFacade
    val effectiveLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)

    try {
      doTextTest(
        "class Foo {\n" + "    void foo() {\n" + "        assert i + j + k + l + n + m <= 2 : \"assert description\";" + "    }\n" + "}",
        "class Foo {\n" +
        "  void foo() {\n" +
        "    assert i + j + k + l + n + m <= 2\n" +
        "      : \"assert description\";\n" +
        "  }\n" +
        "}")

      settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false

      doTextTest(
        "class Foo {\n" + "    void foo() {\n" + "        assert i + j + k + l + n + m <= 2 : \"assert description\";" + "    }\n" + "}",
        "class Foo {\n" +
        "  void foo() {\n" +
        "    assert\n" +
        "      i + j + k + l + n + m <= 2 :\n" +
        "      \"assert description\";\n" +
        "  }\n" +
        "}")
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, effectiveLanguageLevel)
    }

  }

  fun test() {
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 2
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 2
    settings.RIGHT_MARGIN = 37
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true

    settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED

    settings.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true

    val facade = javaFacade
    val effectiveLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)


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
                      "}")
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, effectiveLanguageLevel)
    }
  }

  fun testLBrace() {
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    settings.RIGHT_MARGIN = 14
    doTextTest("class Foo {\n" + "    void foo() {\n" + "        \n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "\n" + "    }\n" + "}")
  }

  fun testJavaDocLeadingAsterisksAreDisabled() {
    javaSettings.JD_LEADING_ASTERISKS_ARE_ENABLED = false
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
                    "}")
  }

  fun testBinaryExpression() {
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true
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
                    "}")
  }

  fun testCaseBraces() {
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
                    "}")
  }

  fun testFormatCodeFragment() {
    val factory = JavaCodeFragmentFactory.getInstance(project)
    val fragment = factory.createCodeBlockCodeFragment("a=1;int b=2;", null, true)
    val result = arrayOfNulls<PsiElement>(1)

    CommandProcessor.getInstance().executeCommand(project, {
      WriteCommandAction.runWriteCommandAction(null) {
        try {
          result[0] = CodeStyleManager.getInstance(project).reformat(fragment)
        }
        catch (e: IncorrectOperationException) {
          fail(e.localizedMessage)
        }
      }
    }, null, null)

    assertEquals("a = 1;\n" + "int b = 2;", result[0]!!.text)
  }

  fun testNewLineAfterJavaDocs() {
    val before = "/** @noinspection InstanceVariableNamingConvention*/class Foo{\n" +
                 "/** @noinspection InstanceVariableNamingConvention*/int myFoo;\n" +
                 "/** @noinspection InstanceVariableNamingConvention*/ void foo(){}}"

    val after = "/**\n" +
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
                "}"

    doTextTest(before, after)
  }

  fun testArrayInitializerWrapping() {
    settings.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false
    settings.RIGHT_MARGIN = 37

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
                    "}")

    settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true

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
                    "}")

  }

  fun testJavaDocIndentation() {
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 2
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 2
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 4

    javaSettings.ENABLE_JAVADOC_FORMATTING = false

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
                    "}")
  }

  fun testRemoveLineBreak() {
    settings.KEEP_LINE_BREAKS = true
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE

    doTextTest("class A\n" + "{\n" + "}", "class A {\n" + "}")

    doTextTest("class A {\n" + "    void foo()\n" + "    {\n" + "    }\n" + "}", "class A {\n" + "    void foo() {\n" + "    }\n" + "}")

    doTextTest("class A {\n" + "    void foo()\n" + "    {\n" + "        if (a)\n" + "        {\n" + "        }\n" + "    }\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        if (a) {\n" + "        }\n" + "    }\n" + "}")

  }

  fun testBlankLines() {
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
    settings.KEEP_BLANK_LINES_IN_CODE = 0
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0
    settings.BLANK_LINES_AFTER_CLASS_HEADER = 0
    settings.BLANK_LINES_AFTER_IMPORTS = 0
    settings.BLANK_LINES_AFTER_PACKAGE = 0
    settings.BLANK_LINES_AROUND_CLASS = 0
    settings.BLANK_LINES_AROUND_FIELD = 0
    settings.BLANK_LINES_AROUND_METHOD = 0
    settings.BLANK_LINES_BEFORE_IMPORTS = 0
    settings.BLANK_LINES_BEFORE_PACKAGE = 0
    settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 2
    settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 3

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
               "}")

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
               "}")

  }

  fun testStaticBlockBraces() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}")

    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}")

    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static\n" + "    {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}")


    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "        }\n" + "}",
               "class Foo {\n" + "    static\n" + "        {\n" + "        //comment\n" + "        i = foo();\n" + "        }\n" + "}")


    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                      "    static\n" +
                                                                                                                      "        {\n" +
                                                                                                                      "            //comment\n" +
                                                                                                                      "            i = foo();\n" +
                                                                                                                      "        }\n" +
                                                                                                                      "}")


  }

  fun testBraces2() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
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
                    "}")

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
                    "}")


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
                    "}")


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
                    "}")

    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED

    doTextTest("class Foo{\n" + "    /**\n" + "     *\n" + "     */\n" + "    void foo() {\n" + "    }\n" + "}",
               "class Foo {\n" + "    /**\n" + "     *\n" + "     */\n" + "    void foo() {\n" + "    }\n" + "}")


    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED

    doTextTest("/**\n" + " *\n" + " */\n" + "class Foo\n{\n" + "}", "/**\n" + " *\n" + " */\n" + "class Foo {\n" + "}")

    doTextTest("/**\n" + " *\n" + " */\n" + "class Foo\n extends B\n{\n" + "}",
               "/**\n" + " *\n" + " */\n" + "class Foo\n        extends B\n" + "{\n" + "}")

    doTextTest("/**\n" + " *\n" + " */\n" + "class Foo\n permits B\n{\n" + "}",
               "/**\n" + " *\n" + " */\n" + "class Foo\n        permits B\n" + "{\n" + "}")

  }

  fun testSynchronized() {

    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this) {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "        }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}")

    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this)\n" +
                                                                                                                   "        {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "        }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}")

    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this)\n" +
                                                                                                                   "            {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "            }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}")


    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {\n" + "foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                          "    void foo() {\n" +
                                                                                                                          "        synchronized (this)\n" +
                                                                                                                          "            {\n" +
                                                                                                                          "                foo();\n" +
                                                                                                                          "            }\n" +
                                                                                                                          "    }\n" +
                                                                                                                          "}")

  }

  fun testNextLineShiftedForBlockStatement() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED

    doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a)\n" + "        foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        if (a)\n" + "            foo();\n" + "    }\n" + "}")
  }

  fun testFieldWithJavadocAndAnnotation() {
    doTextTest("class Foo {\n" + "    /**\n" + "     * java doc\n" + "     */\n" + "    @NoInspection\n" + "    String field;\n" + "}",
               "class Foo {\n" + "    /**\n" + "     * java doc\n" + "     */\n" + "    @NoInspection\n" + "    String field;\n" + "}")
  }

  fun testLongCallChainAfterElse() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    settings.ELSE_ON_NEW_LINE = false
    settings.RIGHT_MARGIN = 110
    settings.KEEP_LINE_BREAKS = false
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
                    "}")
  }

  fun testSpacesIncode() {

    val facade = javaFacade
    val level = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)

    try {
      doTextTest("class C<Y, X> {\n" + "}", "class C<Y, X> {\n" + "}")

      settings.SPACE_BEFORE_METHOD_LBRACE = true
      settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true

      doTextTest("enum En {\n" + "    A(10) {},\n" + "    B(10) {},\n" + "    C(10);\n" + "\n" + "    En(int i) { }\n" + "}",
                 "enum En {\n" + "    A(10) {},\n" + "    B(10) {},\n" + "    C(10);\n" + "\n" + "    En(int i) {}\n" + "}")

      doTextTest("class C {\n" +
                 "    void foo (Map<?, String> s) {\n" +
                 "        Set<? extends Map<?, String>.Entry<?, String>> temp = s.entries();\n" +
                 "    }\n" +
                 "}", "class C {\n" +
                      "    void foo(Map<?, String> s) {\n" +
                      "        Set<? extends Map<?, String>.Entry<?, String>> temp = s.entries();\n" +
                      "    }\n" +
                      "}")

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
                      "}")
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, level)
    }

  }

  ///IDEA-7761
  fun testKeepBlankLineInCodeBeforeComment() {
    settings.KEEP_BLANK_LINES_IN_CODE = 1
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
    settings.KEEP_FIRST_COLUMN_COMMENT = false

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
                    "}")

    settings.KEEP_BLANK_LINES_IN_CODE = 0
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 1
    settings.KEEP_FIRST_COLUMN_COMMENT = false

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
                    "}")

  }

  fun testSpaceBeforeTryBrace() {
    settings.SPACE_BEFORE_TRY_LBRACE = false
    settings.SPACE_BEFORE_FINALLY_LBRACE = true
    doTextTest("class Foo{\n" + "    void foo() {\n" + "        try {\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        try{\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}")

    settings.SPACE_BEFORE_TRY_LBRACE = true
    settings.SPACE_BEFORE_FINALLY_LBRACE = false

    doTextTest("class Foo{\n" + "    void foo() {\n" + "        try {\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        try {\n" + "        } finally{\n" + "        }\n" + "    }\n" + "}")

  }

  fun testFormatComments() {
    javaSettings.ENABLE_JAVADOC_FORMATTING = true
    doTextTest("public class Test {\n" + "\n" + "    /**\n" + "     * The s property.\n" + "     */\n" + "    private String s;\n" + "}",
               "public class Test {\n" + "\n" + "    /**\n" + "     * The s property.\n" + "     */\n" + "    private String s;\n" + "}")

  }

  @Throws(IncorrectOperationException::class)
  fun testDoNotWrapLBrace() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    settings.RIGHT_MARGIN = 66
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
                    "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testNewLinesAroundArrayInitializer() {
    settings.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true
    settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true
    settings.RIGHT_MARGIN = 40
    doTextTest("class Foo {\n" + "    int[] a = new int[]{1,2,0x0052,0x0053,0x0054,0x0054,0x0054};\n" + "}", "class Foo {\n" +
                                                                                                             "    int[] a = new int[]{\n" +
                                                                                                             "            1, 2, 0x0052, 0x0053,\n" +
                                                                                                             "            0x0054, 0x0054, 0x0054\n" +
                                                                                                             "    };\n" +
                                                                                                             "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testSpaceAfterCommaInEnum() {
    settings.SPACE_AFTER_COMMA = true

    val facade = javaFacade
    val effectiveLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)
    try {
      doTextTest("public enum StringExDirection {\n" + "\n" + "    RIGHT_TO_LEFT, LEFT_TO_RIGHT\n" + "\n" + "}",
                 "public enum StringExDirection {\n" + "\n" + "    RIGHT_TO_LEFT, LEFT_TO_RIGHT\n" + "\n" + "}")
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, effectiveLanguageLevel)
    }
  }

  fun testWrapBetweenCommaAndSemicolon() {
      doTextTest("""
enum Foo {
    VALUE_A,
    VALUE_B,;

    Foo() {
        // whatever
    }
}
            """.trimIndent(),
                 """
enum Foo {
    VALUE_A,
    VALUE_B,
    ;

    Foo() {
        // whatever
    }
}
                 """.trimIndent())
  }

  @Throws(IncorrectOperationException::class)
  fun testRemoveBraceBeforeInstanceOf() {
    doTextTest("class ReformatInstanceOf {\n" +
               "    void foo(Object string) {\n" +
               "        if (string.toString() instanceof String) {} // reformat me\n" +
               "    }\n" +
               "}", "class ReformatInstanceOf {\n" +
                    "    void foo(Object string) {\n" +
                    "        if (string.toString() instanceof String) {\n" +
                    "        } // reformat me\n" +
                    "    }\n" +
                    "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testAnnotationBeforePackageLocalConstructor() {
    doTextTest("class Foo {\n" + "    @MyAnnotation Foo() {\n" + "    }\n" + "}",
               "class Foo {\n" + "    @MyAnnotation\n" + "    Foo() {\n" + "    }\n" + "}")
  }

  fun testLongAnnotationsAreNotWrapped() {
    settings.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    doTest()
  }

  fun testWrapExtendsList() {
    settings.RIGHT_MARGIN = 50
    settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED

    doTextTest("class ColtreDataProvider extends DataProvider, AgentEventListener, ParameterDataEventListener {\n}",
               "class ColtreDataProvider extends DataProvider,\n" +
               "        AgentEventListener,\n" +
               "        ParameterDataEventListener {\n}")
  }

  fun testWrapLongExpression() {
    settings.RIGHT_MARGIN = 80
    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true
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
                    "}")
  }

  fun testDoNotWrapCallChainIfParametersWrapped() {
    settings.RIGHT_MARGIN = 87
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    //getSettings().PREFER_PARAMETERS_WRAP = true;

    doMethodTest(
      //9                    30                            70         80    86
      "descriptors = manager.createProblemDescriptor(parameter1, parameter2, parameterparameterpar3,parameter4);",

      "descriptors = manager.createProblemDescriptor(parameter1, parameter2,\n" +
      "                                              parameterparameterpar3,\n" +
      "                                              parameter4);"

    )
  }

  fun testAlignTernaryOperation() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doMethodTest("String s = x == 0 ? \"hello\" :\n" +
                 "                x == 5 ? \"something else\" :\n" +
                 "                        x > 0 ? \"bla, bla\" :\n" +
                 "                                \"\";", "String s = x == 0 ? \"hello\" :\n" +
                                                          "           x == 5 ? \"something else\" :\n" +
                                                          "           x > 0 ? \"bla, bla\" :\n" +
                                                          "           \"\";")

    settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = true

    doMethodTest("int someVariable = a ?\n" + "x :\n" + "y;",
                 "int someVariable = a ?\n" + "                   x :\n" + "                   y;")
  }

  fun testRightMargin_2() {
    settings.RIGHT_MARGIN = 65
    settings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = true
    settings.KEEP_LINE_BREAKS = false

    doClassTest(
      "public static final Map<LongType, LongType> longVariableName =\n" + "variableValue;",
      "public static final Map<LongType, LongType> longVariableName\n" + "        = variableValue;")
  }

  fun testRightMargin_3() {
    settings.RIGHT_MARGIN = 65
    settings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false
    settings.KEEP_LINE_BREAKS = false

    doClassTest(
      "public static final Map<LongType, LongType> longVariableName =\n" + "variableValue;",
      "public static final Map<LongType, LongType>\n" + "        longVariableName = variableValue;")
  }

  fun testDoNotRemoveLineBreaksBetweenComments() {
    settings.KEEP_LINE_BREAKS = false
    settings.KEEP_FIRST_COLUMN_COMMENT = false

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
      "}")
  }

  fun testWrapParamsOnEveryItem() {
    val codeStyleSettings = CodeStyle.getSettings(project)

    val javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE)
    val oldMargin = javaSettings.RIGHT_MARGIN
    val oldKeep = javaSettings.KEEP_LINE_BREAKS
    val oldWrap = javaSettings.METHOD_PARAMETERS_WRAP

    try {
      codeStyleSettings.setRightMargin(JavaLanguage.INSTANCE, 80)
      javaSettings.KEEP_LINE_BREAKS = false
      javaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM

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
        "}")
    }
    finally {
      codeStyleSettings.setRightMargin(JavaLanguage.INSTANCE, oldMargin)
      javaSettings.KEEP_LINE_BREAKS = oldKeep
      javaSettings.METHOD_PARAMETERS_WRAP = oldWrap
    }

  }

  fun testCommentAfterDeclaration() {
    val codeStyleSettings = CodeStyle.getSettings(project)
    val javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE)

    val oldWrap = javaSettings.ASSIGNMENT_WRAP

    try {
      codeStyleSettings.defaultRightMargin = 20
      javaSettings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
      doMethodTest(
        "int i=0; //comment comment",
        "int i =\n" + "        0; //comment comment"
      )

    }
    finally {
      javaSettings.ASSIGNMENT_WRAP = oldWrap
    }
  }

  // ------------------------------------------------
  //              Tickets-implied tests
  // ------------------------------------------------

  fun testSCR915() {
    settings.SPACE_AROUND_ADDITIVE_OPERATORS = false
    doTest("SCR915.java", "SCR915_after.java")
  }

  fun testSCR429() {
    val settings = settings
    settings.KEEP_BLANK_LINES_IN_CODE = 2
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 2
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 2
    doTest()
  }

  fun testSCR548() {
    val settings = settings
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 2
    doTest()
  }

  fun testIDEA_16151() {
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
                     "}")

  }

  fun testIDEA_14324() {
    settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true

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
      "}")

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

    )
  }

  fun testSCR260() {
    val settings = settings
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    settings.KEEP_LINE_BREAKS = false
    doTest()
  }

  fun testSCR114() {
    val settings = settings
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.CATCH_ON_NEW_LINE = true
    doTest()
  }

  fun testSCR259() {
    myTextRange = TextRange(36, 60)
    val settings = settings
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    settings.KEEP_LINE_BREAKS = false
    doTest()
  }

  fun testSCR279() {
    val settings = settings
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testSCR395() {
    val settings = settings
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    doTest()
  }

  fun testSCR11799() {
    val settings = settings
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 4
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
  }

  fun testSCR501() {
    val settings = settings
    settings.KEEP_FIRST_COLUMN_COMMENT = true
    doTest()
  }

  fun testSCR879() {
    val settings = settings
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
  }


  fun testSCR547() {
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
                    "}")
  }

  fun testSCR479() {
    val settings = settings
    settings.RIGHT_MARGIN = 80
    settings.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
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
                    "}")
  }

  fun testSCR190() {
    val settings = settings
    settings.KEEP_LINE_BREAKS = false
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
                    "}")
  }

  fun testSCR1535() {
    val settings = settings
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
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
                    "}")
  }

  fun testSCR970() {
    val settings = settings
    settings.THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    settings.THROWS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    doTest()
  }

  fun testSCR1486() {
    doTextTest("public class Test {\n" + "  private BigDecimal\n" + "}", "public class Test {\n" + "    private BigDecimal\n" + "}")

    doTextTest("public class Test {\n" + "  @NotNull private BigDecimal\n" + "}",
               "public class Test {\n" + "    @NotNull\n" + "    private BigDecimal\n" + "}")

  }

  fun test1607() {
    settings.RIGHT_MARGIN = 30
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    settings.ALIGN_MULTILINE_PARAMETERS = true
    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    doTextTest("class TEst {\n" + "void foo(A a,B b){ /* compiled code */ }\n" + "}",
               "class TEst {\n" + "    void foo(A a, B b)\n" + "    { /* compiled code */ }\n" + "}")
  }

  fun testSCR1615() {
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED

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
      "    }")
  }

  fun testSCR1047() {
    doTextTest("class Foo{\n" + "    void foo(){\n" + "        String field1, field2;\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        String field1, field2;\n" + "    }\n" + "}")
  }

  fun testSCR524() {
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    doTextTest("class Foo {\n" + "    void foo() { return;}" + "}", "class Foo {\n" + "    void foo() {return;}\n" + "}")

    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    settings.CASE_STATEMENT_ON_NEW_LINE = false

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
                    "}")
  }

  fun testSCR3062() {
    settings.KEEP_LINE_BREAKS = false
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    settings.RIGHT_MARGIN = 80

    settings.PREFER_PARAMETERS_WRAP = true

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
      "}")

    settings.PREFER_PARAMETERS_WRAP = false

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
      "}")

  }

  fun testSCR1658() {
    doTextTest("/** \n" + " * @author\tMike\n" + " */\n" + "public class Foo {\n" + "}",
               "/**\n" + " * @author Mike\n" + " */\n" + "public class Foo {\n" + "}")
  }

  fun testSCR1699() {
    doTextTest("class Test {\n" + "    Test(String t1 , String t2) {\n" + "    }\n" + "}",
               "class Test {\n" + "    Test(String t1, String t2) {\n" + "    }\n" + "}")
  }

  fun testSCR1700() {
    doTextTest("class Test {\n" + "    Test(String      t1 , String      t2) {\n" + "    }\n" + "}",
               "class Test {\n" + "    Test(String t1, String t2) {\n" + "    }\n" + "}")
  }

  fun testSCR1701() {
    settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    settings.SPACE_WITHIN_METHOD_PARENTHESES = false
    settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    doTextTest("class Foo {\n" + "    void foo() {\n" + "        foo(a,b);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        foo( a, b );\n" + "    }\n" + "}")
  }

  fun testSCR1703() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
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
                    "}")
  }

  fun testSCR1804() {
    settings.ALIGN_MULTILINE_ASSIGNMENT = true
    doTextTest(
      "class Foo {\n" + "    void foo() {\n" + "        int i;\n" + "        i = \n" + "                1 + 2;\n" + "    }\n" + "}",
      "class Foo {\n" + "    void foo() {\n" + "        int i;\n" + "        i =\n" + "                1 + 2;\n" + "    }\n" + "}")

    doTextTest("class Foo {\n" + "    void foo() {\n" + "        i = j =\n" + "        k = l = 1 + 2;\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        i = j =\n" + "        k = l = 1 + 2;\n" + "    }\n" + "}")

  }

  fun testSCR1795() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
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
                    "}")
  }

  fun testSCR1936() {
    settings.BLANK_LINES_AFTER_CLASS_HEADER = 4
    doTextTest("/**\n" + " * Foo - test class\n" + " */\n" + "class Foo{\n" + "}",
               "/**\n" + " * Foo - test class\n" + " */\n" + "class Foo {\n" + "\n" + "\n" + "\n" + "\n" + "}")

    doTextTest("/**\n" + " * Foo - test class\n" + " */\n" + "class Foo{\n" + "    int myFoo;\n" + "}",
               "/**\n" + " * Foo - test class\n" + " */\n" + "class Foo {\n" + "\n" + "\n" + "\n" + "\n" + "    int myFoo;\n" + "}")

  }

  fun test1980() {
    settings.RIGHT_MARGIN = 144
    settings.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = true
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
                    "}")
  }

  fun testSCR2089() {
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
                    "}")
  }

  fun testSCR2132() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    settings.ELSE_ON_NEW_LINE = true

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
                    "}")
  }

  fun testIDEADEV1047() {
    doTextTest("class Foo{\n" + "String field1\n" + ",\n" + "field2\n" + ";" + "}",
               "class Foo {\n" + "    String field1,\n" + "            field2;\n" + "}")

    doTextTest("class Foo{\n" + "void foo() {\n" + "    String var1\n" + ",\n" + "var2\n" + ";\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        String var1,\n" + "                var2;\n" + "    }\n" + "}")

  }

  fun testIDEADEV1047_2() {
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
    )
  }

  fun testSCR2241() {
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    settings.SPECIAL_ELSE_IF_TREATMENT = true
    settings.ELSE_ON_NEW_LINE = true
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
                    "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testSCRIDEA_4783() {
    settings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.RIGHT_MARGIN = 80

    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        final CommandRouterProtocolHandler protocolHandler =\n" +
                    "                (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "    }\n" +
                    "}")


    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        protocolHandlerCommandRouterProtocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        protocolHandlerCommandRouterProtocolHandler =\n" +
                    "                (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "    }\n" +
                    "}")

    doTextTest("class Foo{\n" +
               "    final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "}", "class Foo {\n" +
                    "    final CommandRouterProtocolHandler protocolHandler =\n" +
                    "            (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "}")

    settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = true

    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        final CommandRouterProtocolHandler protocolHandler\n" +
                    "                = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "    }\n" +
                    "}")

    doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        protocolHandlerCommandRouterProtocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        protocolHandlerCommandRouterProtocolHandler\n" +
                    "                = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "    }\n" +
                    "}")


    doTextTest("class Foo{\n" +
               "    final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "}", "class Foo {\n" +
                    "    final CommandRouterProtocolHandler protocolHandler\n" +
                    "            = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "}")

  }

  @Throws(IncorrectOperationException::class)
  fun testSCRIDEADEV_2292() {
    settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    settings.WHILE_ON_NEW_LINE = true

    val facade = javaFacade
    val stored = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)

    try {
      doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        if (a)\n" +
                 "            foo();\n" +
                 "        else\n" +
                 "            bar();\n" +
                 "    }\n" +
                 "}")


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        for (int i = 0; i < 10; i++)\n" +
                 "            foo();\n" +
                 "    }\n" +
                 "}")


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars)\n" + "            foo();\n" + "    }\n" + "}")


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                 "    void foo() {\n" +
                                                                                                                 "        do\n" +
                                                                                                                 "            foo();\n" +
                                                                                                                 "        while (true);\n" +
                                                                                                                 "    }\n" +
                                                                                                                 "}")


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true)\n" + "            foo();\n" + "    }\n" + "}")

      settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true
      settings.WHILE_ON_NEW_LINE = false

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}")

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}")


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}")


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}")


      doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true) foo();\n" + "    }\n" + "}")

      settings.RIGHT_MARGIN = 17

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        if (a)\n" +
                 "            foo();\n" +
                 "        else\n" +
                 "            bar();\n" +
                 "    }\n" +
                 "}")

      settings.RIGHT_MARGIN = 30

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        for (int i = 0; i < 10; i++)\n" +
                 "            foo();\n" +
                 "    }\n" +
                 "}")

      settings.RIGHT_MARGIN = 32
      doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars)\n" + "            foo();\n" + "    }\n" + "}")


      settings.RIGHT_MARGIN = 12
      doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                 "    void foo() {\n" +
                                                                                                                 "        do\n" +
                                                                                                                 "            foo();\n" +
                                                                                                                 "        while (true);\n" +
                                                                                                                 "    }\n" +
                                                                                                                 "}")

      settings.RIGHT_MARGIN = 23

      doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true)\n" + "            foo();\n" + "    }\n" + "}")

    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, stored)
    }


  }

  fun testSCR3115() {
    val indentOptions = settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE)
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.SMART_TABS = true

    settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true

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
                    "}")
  }

  fun testIDEADEV_6239() {
    javaSettings.ENABLE_JAVADOC_FORMATTING = true
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
                    "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testIDEADEV_8755() {
    settings.KEEP_LINE_BREAKS = false
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
                    "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testIDEADEV_24168() {
    doTextTest(
      "class Foo {\n" + "@AnExampleMethod\n" + "public String\n" + "getMeAString()\n" + "throws AnException\n" + "{\n" + "\n" + "}\n" + "}",
      "class Foo {\n" +
      "    @AnExampleMethod\n" +
      "    public String\n" +
      "    getMeAString()\n" +
      "            throws AnException {\n" +
      "\n" +
      "    }\n" +
      "}")
  }


  @Throws(IncorrectOperationException::class)
  fun testIDEADEV_2541() {
    myTextRange = TextRange(0, 15)
    doTextTest("/** @param q */\nclass Foo {\n}", "/**\n" + " * @param q\n" + " */\n" + "class Foo {\n" + "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testIDEADEV_6434() {
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true
    settings.ALIGN_MULTILINE_ASSIGNMENT = true
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
                    "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testIDEADEV_12836() {
    settings.SPECIAL_ELSE_IF_TREATMENT = true
    settings.RIGHT_MARGIN = 80
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
                    "}")

  }
  /*
  public void testIDEADEV_26871() throws IncorrectOperationException {
    getSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 4;
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

  @Throws(IncorrectOperationException::class)
  fun test23551() {
    doTextTest("public class Wrapping {\n" +
               "    public static void sample() {\n" +
               "        System.out.println(\".\" + File.separator + \"..\" + File.separator + \"some-directory-name\" + File.separator + \"more-file-name\");\n" +
               "    }\n" +
               "}", "public class Wrapping {\n" +
                    "    public static void sample() {\n" +
                    "        System.out.println(\".\" + File.separator + \"..\" + File.separator + \"some-directory-name\" + File.separator + \"more-file-name\");\n" +
                    "    }\n" +
                    "}")
  }

  /*
  public void testIDEADEV_26871_2() throws IncorrectOperationException {
    getSettings().getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 4;
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
  @Throws(IncorrectOperationException::class)
  fun testIDEADEV_23551() {
    settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM

    settings.RIGHT_MARGIN = 60
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
                    "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testIDEADEV_22967() {
    settings.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

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
                    "}")
  }

  @Throws(IncorrectOperationException::class)
  fun testIDEADEV_22967_2() {
    settings.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    doTextTest("public interface TestInterface {\n" + "    @Deprecated\n" + "    <T> void parametrizedAnnotated(T data);\n" + "}",
               "public interface TestInterface {\n" + "    @Deprecated\n" + "    <T> void parametrizedAnnotated(T data);\n" + "}")
  }

  fun testIDEA_54406() {
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
               "}")
  }

  fun testCommaInMethodDeclParamsList() {
    settings.SPACE_AFTER_COMMA = true
    var before = "public class Test {\n" +
                 "    public static void act(   int a   ,    int b   ,    int c   ,      int d) {\n" +
                 "        act(1,2,3,4);\n" +
                 "    }\n" +
                 "}\n"
    var after = "public class Test {\n" +
                "    public static void act(int a, int b, int c, int d) {\n" +
                "        act(1, 2, 3, 4);\n" +
                "    }\n" +
                "}\n"
    doTextTest(before, after)
    settings.SPACE_AFTER_COMMA = false
    before = "public class Test {\n" +
             "    public static void act(   int a   ,    int b   ,      int c   ,      int d) {\n" +
             "        act(1 ,   2 , 3 ,            4);\n" +
             "    }\n" +
             "}\n"
    after = "public class Test {\n" +
            "    public static void act(int a,int b,int c,int d) {\n" +
            "        act(1,2,3,4);\n" +
            "    }\n" +
            "}\n"
    doTextTest(before, after)
  }


  fun testFormatterOnOffTags() {
    settings.rootSettings.FORMATTER_TAGS_ENABLED = true
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    doTest()
  }

  fun testFormatterOnOffTagsWithRegexp() {
    val settings = settings.rootSettings
    settings.FORMATTER_TAGS_ENABLED = true
    settings.FORMATTER_TAGS_ACCEPT_REGEXP = true
    settings.FORMATTER_OFF_TAG = "not.*format"
    settings.FORMATTER_ON_TAG = "end.*fragment"
    doTest()
  }

  fun testDoNotIndentNotSelectedStatement_AfterSelectedOne() {
    myTextRange = TextRange(0, 73)
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
    )

    myTextRange = TextRange(0, 67)
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
    )
  }

  fun testDetectableIndentOptions() {
    val original = "public class Main {\n" +
                   "\tpublic void main() {\n" +
                   "try {\n" +
                   "\t\t\tSystem.out.println();\n" +
                   "\t} catch (java.lang.Exception exception) {\n" +
                   "\t\t\texception.printStackTrace();\n" +
                   "\t}\n" +
                   "}\n" +
                   "}"
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
    )
    // Reformat with a smaller text range (detection is on)
    myTextRange = TextRange(1, original.length)
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
    )
  }

  fun testKeepIndentsOnEmptyLines() {
    val indentOptions = settings.indentOptions
    assertNotNull(indentOptions)
    val original = "public class Main {\n" +
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
                   "}"

    indentOptions!!.KEEP_INDENTS_ON_EMPTY_LINES = false
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
    )
    indentOptions.KEEP_INDENTS_ON_EMPTY_LINES = true
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
    )
  }

  fun testIdea114862() {
    settings.rootSettings.FORMATTER_TAGS_ENABLED = true
    val indentOptions = settings.indentOptions
    assertNotNull(indentOptions)
    indentOptions!!.USE_TAB_CHARACTER = true
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
    )
  }

  fun testIdea184461() {
    settings.rootSettings.FORMATTER_TAGS_ENABLED = true
    val indentOptions = settings.indentOptions
    assertNotNull(indentOptions)
    indentOptions!!.USE_TAB_CHARACTER = true
    doTextTest(
      "public class Test {\n" +
      "/* @formatter:off */\n" +
      "      String foo;\n" +
      "      String bar;\n" +
      "/* @formatter:on */\n\n" +
      "      String abc;\n" +
      "}",

      "public class Test {\n" +
      "\t/* @formatter:off */\n" +
      "      String foo;\n" +
      "      String bar;\n" +
      "/* @formatter:on */\n\n" +
      "\tString abc;\n" +
      "}"

    )
  }

  fun testReformatCodeWithErrorElementsWithoutAssertions() {
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
               "}  ")
  }

  fun testReformatPackageAnnotation() {
    doTextTest(
      "@ParametersAreNonnullByDefault package com.example;",
      "@ParametersAreNonnullByDefault\n" + "package com.example;"
    )

    doTextTest(
      "        @ParametersAreNonnullByDefault\n" + "package com.example;",
      "@ParametersAreNonnullByDefault\n" + "package com.example;"
    )
  }

  fun testFinal_OnTheEndOfLine() {
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
    )
  }

  fun testKeepTypeAnnotationNearType() {
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
    )
  }

  fun testKeepSimpleSwitchInOneLine() {
    settings.CASE_STATEMENT_ON_NEW_LINE = false
    doMethodTest(
      "switch (b) {\n" +
      "case 1: case 2: break;\n" +
      "}",
      "switch (b) {\n" +
      "    case 1: case 2: break;\n" +
      "}")
  }

  fun testExpandSwitch() {
    settings.CASE_STATEMENT_ON_NEW_LINE = false
    doMethodTest(
      "switch (b) {\n" +
      "case 1: { println(1); } case 2: break;\n" +
      "}",
      "switch (b) {\n" +
      "    case 1: {\n" +
      "        println(1);\n" +
      "    }\n" +
      "    case 2: break;\n" +
      "}")
  }

  fun testKeepBreakOnSameLine() {
    settings.CASE_STATEMENT_ON_NEW_LINE = false
    doMethodTest(
      "switch (b) {\n" +
      "case 1: case 2:\n" +
      "\n\n\n\n\n\n" +
      "break;\n" +
      "}",
      "switch (b) {\n" +
      "    case 1: case 2:\n\n\n" +
      "        break;\n" +
      "}")
  }

  fun testAnnotationAndFinalInsideParamList() {
    doClassTest(
      "public class Test {\n" +
      "    void test(@Nullable    final String childFallbackProvider) {\n" +
      "    }\n" +
      "}",
      "public class Test {\n" +
      "    void test(@Nullable final String childFallbackProvider) {\n" +
      "    }\n" +
      "}"
    )
  }

  fun testPreserveRbraceOnItsLine() {
    doClassTest(
      "class SomeTest {\n" +
      "  @Test\n" +
      "}",
      "class SomeTest {\n" +
      "    @Test\n" +
      "}"
    )
  }

  fun testFormatCStyleCommentWithAsterisks() {
    doMethodTest(
      "        for (Object o : new Object[]{}) {\n" +
      "/*\n" +
      "        *\t\n" +
      " \t\t\t\t\t            */\n" +
      "        }\n",
      "for (Object o : new Object[]{}) {\n" +
      "    /*\n" +
      "     *\n" +
      "     */\n" +
      "}\n"
    )
  }

  fun testIdea183193() {
    doTextTest(
      "package de.tarent.bugreport;\n" +
      "\n" +
      "        /*-\n" +
      "         * This is supposed\n" +
      "         * to be a copyright comment\n" +
      "         * and thus not wrapped.\n" +
      "         */\n" +
      "\n" +
      "        /*\n" +
      "         * This is supposed\n" +
      "         * to be wrapped.\n" +
      "         */\n" +
      "\n" +
      "/**\n" +
      " * This is JavaDoc.\n" +
      " */\n" +
      "public class IndentBugReport {\n" +
      "}",

      "package de.tarent.bugreport;\n" +
      "\n" +
      "/*-\n" +
      " * This is supposed\n" +
      " * to be a copyright comment\n" +
      " * and thus not wrapped.\n" +
      " */\n" +
      "\n" +
      "/*\n" +
      " * This is supposed\n" +
      " * to be wrapped.\n" +
      " */\n" +
      "\n" +
      "/**\n" +
      " * This is JavaDoc.\n" +
      " */\n" +
      "public class IndentBugReport {\n" +
      "}"
    )
  }

  /**
   * See IDEA-98552, IDEA-175560
   */
  fun testParenthesesIndentation() {
    doTextTest(
"""
package com.company;

import java.util.Arrays;
import java.util.List;

class Test {

void foo(boolean bool1, boolean bool2) {
List<String> list = Arrays.asList(
"a", "b", "c"
);
 // IDEA-98552
for (
String s : list
) {
System.out.println(s);
}
 // IDEA-175560
if (bool1
&& bool2
) { // Indented at continuation indent level
 // do stuff
}
}
}
""",

"""
package com.company;

import java.util.Arrays;
import java.util.List;

class Test {

    void foo(boolean bool1, boolean bool2) {
        List<String> list = Arrays.asList(
                "a", "b", "c"
        );
        // IDEA-98552
        for (
                String s : list
        ) {
            System.out.println(s);
        }
        // IDEA-175560
        if (bool1
                && bool2
        ) { // Indented at continuation indent level
            // do stuff
        }
    }
}
"""
    )
  }

  fun testBlankLinesBeforeClassEnd () {
    settings.BLANK_LINES_BEFORE_CLASS_END = 2
    doTextTest(
"""
public class Test {

    private int field1;
    private int field2;

    {
        field1 = 2;
    }

    public void test() {
        new Runnable() {
            public void run() {
            }
        };
    }
}
""",

"""
public class Test {

    private int field1;
    private int field2;

    {
        field1 = 2;
    }

    public void test() {
        new Runnable() {
            public void run() {
            }
        };
    }


}
"""
    )
  }

  fun testBlankLinesBeforeClassEnd_afterField () {
    settings.BLANK_LINES_BEFORE_CLASS_END = 2
    doTextTest(
"""
public class Test {

    private int field1;
    private int field2;
}
""",

"""
public class Test {

    private int field1;
    private int field2;


}
"""
    )
  }


  fun testBlankLinesBeforeClassEnd_afterInnerClass () {
    settings.BLANK_LINES_BEFORE_CLASS_END = 2
    doTextTest(
"""
public class Test {

    private class InnerTest {
        private int f;
    }
}
""",

"""
public class Test {

    private class InnerTest {
        private int f;


    }


}
"""
    )
  }

  fun testSpacing() {
    doTextTest(
      "enum A{\n" +
      "  B,\n" +
      "  /** or like this */C();\n" +
      "}",

      "enum A {\n" +
       "    B,\n" +
       "    /**\n" +
       "     * or like this\n" +
       "     */\n" +
       "    C();\n" +
       "}"
    )
  }


  fun testIdea192024() {
    settings.apply{
      RIGHT_MARGIN = 30
    }
    doTextTest(
        """
          public class Main {
          public static void main(String[] args) {
        int longCountVar = 0;
              do {
        System.out.println("Test");
                  longCountVar ++;
              } while(longCountVar <= 1000);
          }
      }""".trimIndent(),

      """
      public class Main {
          public static void main(String[] args) {
              int longCountVar = 0;
              do {
                  System.out.println("Test");
                  longCountVar++;
              } while (longCountVar <= 1000);
          }
      }""".trimIndent()
    )
  }

  fun testIdeaIDEA203464() {
    settings.apply{
      ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
      KEEP_LINE_BREAKS = false
    }
    doTextTest(
        """
/**
 * javadoc
 */
public enum EnumApplyChannel {

    C;

    public String method() {
        return null;
    }
}""".trimIndent(),

      """
/**
 * javadoc
 */
public enum EnumApplyChannel {

    C;

    public String method() {
        return null;
    }
}""".trimIndent()
    )
  }


  fun testIdeaIDEA198408() {
    settings.apply{
      ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
      KEEP_LINE_BREAKS = false
    }
    doTextTest(
        """
/** JavaDoc */
public enum LevelCode {HIGH(3),
   MEDIUM(2),
   LOW(1);

   private final int levelCode;

   LevelCode(int levelCode) {
       this.levelCode = levelCode;
   }
}""".trimIndent(),

      """
/**
 * JavaDoc
 */
public enum LevelCode {
    HIGH(3),
    MEDIUM(2),
    LOW(1);

    private final int levelCode;

    LevelCode(int levelCode) {
        this.levelCode = levelCode;
    }
}""".trimIndent()
    )
  }


  fun testIdea195707() {
    settings.apply {
      CASE_STATEMENT_ON_NEW_LINE = true
      KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true
    }

    doTextTest(
      """
      public enum RotationDirection {
        CLOCKWISE, COUNTERCLOCKWISE;

        public RotationDirection inverse() {
            switch(this) {
                case CLOCKWISE: return COUNTERCLOCKWISE; case COUNTERCLOCKWISE: return CLOCKWISE;
            }
            throw new IllegalArgumentException("Unknown " + getClass().getSimpleName() + ": " + this);
        }
      }
      """.trimIndent(),

      """
      public enum RotationDirection {
          CLOCKWISE, COUNTERCLOCKWISE;

          public RotationDirection inverse() {
              switch (this) {
                  case CLOCKWISE:
                      return COUNTERCLOCKWISE;
                  case COUNTERCLOCKWISE:
                      return CLOCKWISE;
              }
              throw new IllegalArgumentException("Unknown " + getClass().getSimpleName() + ": " + this);
          }
      }
      """.trimIndent()
    )
  }

  fun testIdea195707_1() {
    settings.apply {
      CASE_STATEMENT_ON_NEW_LINE = false
      KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true
    }

    doTextTest(
      """
      public enum RotationDirection {
        CLOCKWISE, COUNTERCLOCKWISE;

        public RotationDirection inverse() {
            switch(this) {
                case CLOCKWISE: return COUNTERCLOCKWISE; case COUNTERCLOCKWISE: return CLOCKWISE;
            }
            throw new IllegalArgumentException("Unknown " + getClass().getSimpleName() + ": " + this);
        }
      }
      """.trimIndent(),

      """
      public enum RotationDirection {
          CLOCKWISE, COUNTERCLOCKWISE;

          public RotationDirection inverse() {
              switch (this) {
                  case CLOCKWISE: return COUNTERCLOCKWISE;
                  case COUNTERCLOCKWISE: return CLOCKWISE;
              }
              throw new IllegalArgumentException("Unknown " + getClass().getSimpleName() + ": " + this);
          }
      }
      """.trimIndent()
    )
  }

  fun testFormatterTagsDisabled() {
    settings.rootSettings.apply {
      FORMATTER_TAGS_ENABLED = false
    }
    doTextTest(
      """
      package com.company;
      
      public class TestOne {
          // @formatter:off
            public   void  test   (int x)  {
            }
          // @formatter:on
      }
      """.trimIndent(),

      """
      package com.company;
      
      public class TestOne {
          // @formatter:off
          public void test(int x) {
          }
          // @formatter:on
      }
      """.trimIndent()
    )
  }

  fun testRecordHeaderLparenOnNewLine() {
    javaSettings.apply {
      NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = true
    }
    doTextTest("""
      record A(String s
        ) {}
    """.trimIndent(), """
      record A(
              String s
      ) {
      }
    """.trimIndent())
  }

  fun testRecordHeaderLparenNotOnNewLine() {
    javaSettings.apply {
      NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = false
    }
    settings.KEEP_LINE_BREAKS = false
    doTextTest("""
      record A(String s
        ) {}
    """.trimIndent(), """
      record A(String s) {
      }
    """.trimIndent())
  }

  fun testRecordHeaderRparenOnNewLine() {
    javaSettings.apply {
      RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = true
    }
    doTextTest("""
      record A(String s,
            String a) {}
    """.trimIndent(), """
      record A(String s,
               String a
      ) {
      }
    """.trimIndent())
  }

  fun testRecordHeaderRparenNotOnNewLine() {
    javaSettings.apply {
      RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = false
    }
    settings.KEEP_LINE_BREAKS = false
    doTextTest("""
      record A(
            String s,
            String a
      ) {
      }
    """.trimIndent(), """
        record A(String s, String a) {
        }
    """.trimIndent())
  }

  fun testRecordHeaderMultilineAlign() {
    javaSettings.apply {
      NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = false
      ALIGN_MULTILINE_RECORDS = true
    }
    doTextTest("""
      record A(String s,
        String a
      ) {}
    """.trimIndent(), """
      record A(String s,
               String a
      ) {
      }
    """.trimIndent())
  }

  fun testRecordHeaderNotMultilineAlign() {
    javaSettings.apply {
      NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = false
      ALIGN_MULTILINE_RECORDS = false
    }
    doTextTest("""
      record A(String s,
        String a
      ) {}
    """.trimIndent(), """
      record A(String s,
              String a
      ) {
      }
    """.trimIndent())
  }

  fun testInstanceofPatternWithGeneric() {

    doTextTest("""
      class A{
       void foo(Object x) {
        if (x instanceof List<String> a) {}
       }
      }
    """.trimIndent(), """
      class A {
          void foo(Object x) {
              if (x instanceof List<String> a) {
              }
          }
      }
    """.trimIndent())
  }

  fun testIdea154076() {
    doTextTest("""
    public class Test {
    @SuppressWarnings("any")
      String suppressed = null;
      @SuppressWarnings("any") // comment
       String commented = null;

      void f() {
      @SuppressWarnings("any")
      String suppressed = null;
    @SuppressWarnings("any") // comment
            String commented = null;
     }
    }""".trimIndent(), """
    public class Test {
        @SuppressWarnings("any")
        String suppressed = null;
        @SuppressWarnings("any") // comment
        String commented = null;

        void f() {
            @SuppressWarnings("any")
            String suppressed = null;
            @SuppressWarnings("any") // comment
            String commented = null;
        }
    }""".trimIndent()
    )
  }

  fun testPermitsList() {
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true
    doTextTest("sealed class A permits B, \n" + "C {}", "sealed class A permits B,\n" + "                       C {\n}")
  }

  fun testWrapPermitsList() {
    settings.RIGHT_MARGIN = 50
    settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED

    doTextTest("sealed class ColtreDataProvider permits Provider, AgentEventListener, ParameterDataEventListener {\n}",
               "sealed class ColtreDataProvider permits Provider,\n" +
               "        AgentEventListener,\n" +
               "        ParameterDataEventListener {\n}")
  }


  fun testPermitsListWithPrecedingGeneric() {
    doTextTest("""
      sealed class Simple permits B {
      }

      final class B extends Simple {
      }
    """.trimIndent(), """
      sealed class Simple permits B {
      }

      final class B extends Simple {
      }
    """.trimIndent())
  }

  fun testIdea250114() {
    doTextTest("""
      @Deprecated // Comment
      class Test {
      }
      """.trimIndent(),

      """
      @Deprecated // Comment
      class Test {
      }
      """.trimIndent())
  }

  fun testIdea252167() {
    doTextTest("""      
      @Ann record R(String str) {
      }
      """.trimIndent(),

      """
      @Ann
      record R(String str) {
      }
      """.trimIndent())
  }

  fun testIdea153525() {
    settings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    doTextTest(
      """
      public class Test {
      void foo () {
          bar (event ->{for (Listener l : listeners) {
            notify ();
        }
          });
      }
      }
      """.trimIndent(),

      """
      public class Test {
          void foo() {
              bar(event ->
                  {
                  for (Listener l : listeners) {
                      notify();
                  }
                  });
          }
      }
      """.trimIndent()
    )
  }

  fun testFormattingImplicitClassMembers() {
    doTextTest(
      """
      void before() {
      }
      class A {}
      void after() {
      }
      String s = "foo";
      """.trimIndent(),

      """
      void before() {
      }
      
      class A {
      }
      
      void after() {
      }
      
      String s = "foo";
      """.trimIndent()
    )
  }

  fun testDoNotFormatLongChains() {
    settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTest()
  }
}