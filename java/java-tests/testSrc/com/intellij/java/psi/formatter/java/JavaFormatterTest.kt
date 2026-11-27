// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.application.options.CodeStyle
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaCodeFragmentFactory
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.PackageEntry
import com.intellij.psi.codeStyle.PackageEntryTable
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.IncorrectOperationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

/**
 * **Note:** this class is too huge and hard to use. It's tests are intended to be split in multiple more fine-grained
 * java formatting test classes.
 */
@TestApplication
class JavaFormatterTest {
  companion object {
    private val projectFixture = javaFormatterProjectFixture()
  }

  private val formatter by javaFormatterFixture(projectFixture)

  @Test
  fun testPaymentManager() {
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.javaSettings.KEEP_BLANK_LINES_BETWEEN_IMPORTS = true
    formatter.doTest("paymentManager.java", "paymentManager_after.java")
  }

  @Test
  fun testForEach() {
    formatter.doTest("ForEach.java", "ForEach_after.java")
  }

  @Test
  fun testDoubleCast() {
    formatter.doTest("DoubleCast.java", "DoubleCast_after.java")
  }

  @Test
  fun test1() {
    formatter.textRange = TextRange(35, 46)
    formatter.doTest("1.java", "1_after.java")
  }

  @Test
  fun testLabel1() {
    formatter.indentOptions.LABEL_INDENT_SIZE = 0
    formatter.indentOptions.LABEL_INDENT_ABSOLUTE = true
    formatter.doTest("Label.java", "Label_after1.java")
  }

  @Test
  fun testTryCatch() {
    formatter.textRange = TextRange(38, 72)
    formatter.doTest("TryCatch.java", "TryCatch_after.java")
  }

  @Test
  fun testNullMethodParameter() {
    formatter.settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    formatter.settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    formatter.doTest("NullMethodParameter.java", "NullMethodParameter_after.java")
  }

  @Test
  fun test_DoNot_JoinLines_If_KeepLineBreaksIsOn() {
    formatter.settings.KEEP_LINE_BREAKS = true
    formatter.settings.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    formatter.doTextTest(
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

  @Test
  fun test_DoNot_JoinLines_If_KeepLineBreaksIsOn_WithMultipleAnnotations() {
    formatter.settings.KEEP_LINE_BREAKS = true
    formatter.settings.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    formatter.doTextTest(
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

  @Test
  fun test_format_only_selected_range() {
    formatter.textRange = TextRange(18, 19)
    formatter.doTextTest(
      "public class X {\n" +
      " public int a =       2;\n" +
      "}",
      "public class X {\n" +
      "    public int a =       2;\n" +
      "}"
    )
  }

  @Test
  fun testNew() {
    formatter.indentOptions.CONTINUATION_INDENT_SIZE = 8
    formatter.doTest("New.java", "New_after.java")
  }

  @Test
  fun testJavaDoc() {
    formatter.settings.BLANK_LINES_AROUND_FIELD = 1
    formatter.doTest("JavaDoc.java", "JavaDoc_after.java")
  }

  @Test
  fun testBreakInsideIf() {
    formatter.doTest("BreakInsideIf.java", "BreakInsideIf_after.java")
  }

  @Test
  fun testAssert() {
    IdeaTestUtil.setProjectLanguageLevel(formatter.project, LanguageLevel.HIGHEST)
    formatter.doTest()
  }

  @Test
  fun testCastInsideElse() {
    formatter.indentOptions.CONTINUATION_INDENT_SIZE = 2
    formatter.indentOptions.INDENT_SIZE = 2
    formatter.indentOptions.LABEL_INDENT_SIZE = 0
    formatter.indentOptions.TAB_SIZE = 8
    formatter.settings.SPACE_WITHIN_CAST_PARENTHESES = false
    formatter.settings.SPACE_AFTER_TYPE_CAST = true
    formatter.settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true
    formatter.doTest()
  }

  @Test
  fun testAlignMultiLine() {
    formatter.settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = true
    formatter.settings.ALIGN_MULTILINE_BINARY_OPERATION = true
    formatter.doTest()
  }

  @Test
  fun testInnerClassAsParameter() {
    formatter.doTest()
  }

  @Test
  fun testSynchronizedBlock() {
    formatter.settings.apply {
      SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false
      SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = false
      SPACE_BEFORE_SYNCHRONIZED_LBRACE = false
    }
    formatter.doTest()
  }

  @Test
  fun testMethodCallInAssignment() {
    val settings = formatter.settings
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 8
    formatter.doTest()
  }

  @Test
  fun testAnonymousInnerClasses() {
    formatter.doTest()
  }

  @Test
  fun testAnonymousInner2() {
    formatter.doTest()
  }

  @Test
  fun testWrapAssertion() {
    formatter.doTest()
  }

  @Test
  fun testIfElse() {
    formatter.settings.apply {
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
    formatter.doTest()
  }

  @Test
  fun testIfBraces() {
    formatter.settings.apply {
      IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
      BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
      KEEP_LINE_BREAKS = false
    }
    formatter.doTest()
  }

  @Test
  fun testTernaryExpression() {
    formatter.settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    formatter.doTest()

    formatter.settings.ALIGN_MULTILINE_TERNARY_OPERATION = false
    formatter.doTest("TernaryExpression.java", "TernaryExpression_DoNotAlign_after.java")

  }

  @Test
  fun testAlignAssignment() {
    formatter.settings.apply {
      ALIGN_MULTILINE_ASSIGNMENT = true
      ALIGN_MULTILINE_BINARY_OPERATION = true
    }
    formatter.doTest()
  }

  @Test
  fun testAlignFor() {
    formatter.settings.apply {
      ALIGN_MULTILINE_BINARY_OPERATION = true
      ALIGN_MULTILINE_FOR = true
    }
    formatter.doTest()
  }

  @Test
  fun testSwitch() {
    formatter.doTest()
  }

  @Test
  fun testContinue() {
    formatter.doTest()
  }

  @Test
  fun testIf() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.doTest()
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.doTest("If.java", "If.java")
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.doTest("If_after.java", "If.java")

  }

  @Test
  fun test2() {
    formatter.settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    formatter.doTest()
  }

  @Test
  fun testBlocks() {
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.doTest()
  }

  @Throws(IncorrectOperationException::class)
  @Test
  fun testBinaryOperation() {
    val text = "class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        + ccc + eee + ddd;\n" + "    }\n" + "}"

    formatter.settings.apply {
      ALIGN_MULTILINE_BINARY_OPERATION = true
      ALIGN_MULTILINE_ASSIGNMENT = true
    }
    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "              + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")
    formatter.settings.apply {
      ALIGN_MULTILINE_BINARY_OPERATION = true
      ALIGN_MULTILINE_ASSIGNMENT = false
    }
    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "              + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")

    formatter.settings.apply {
      ALIGN_MULTILINE_BINARY_OPERATION = false
      ALIGN_MULTILINE_ASSIGNMENT = true
    }
    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")

    formatter.settings.apply {
      ALIGN_MULTILINE_ASSIGNMENT = false
      ALIGN_MULTILINE_BINARY_OPERATION = false
    }

    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")

    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        xxx = aaa + bbb\n" +
                     "                + ccc + eee + ddd;\n" +
                     "    }\n" +
                     "}")


    formatter.settings.ALIGN_MULTILINE_BINARY_OPERATION = true

    formatter.doTextTest("class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        - ccc + eee + ddd;\n" + "    }\n" + "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        xxx = aaa + bbb\n" +
               "              - ccc + eee + ddd;\n" +
               "    }\n" +
               "}")

    formatter.doTextTest("class Foo {\n" + "    void foo () {\n" + "        xxx = aaa + bbb \n" + "        * ccc + eee + ddd;\n" + "    }\n" + "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        xxx = aaa + bbb\n" +
               "                    * ccc + eee + ddd;\n" +
               "    }\n" +
               "}")

  }

  @Test
  fun testWhile() {
    formatter.doTextTest("class A{\n" + "void a(){\n" + "do x++ while (b);\n" + "}\n}",
               "class A {\n" + "    void a() {\n" + "        do x++ while (b);\n" + "    }\n" + "}")
  }

  @Test
  fun testFor() {
    formatter.doTextTest("class A{\n" + "void b(){\n" + "for (c) {\n" + "d();\n" + "}\n" + "}\n" + "}",
               "class A {\n" + "    void b() {\n" + "        for (c) {\n" + "            d();\n" + "        }\n" + "    }\n" + "}")
  }

  @Test
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
    formatter.doTextTest(before, after)
  }

  @Test
  fun testStringBinaryOperation() {
    formatter.settings.apply {
      ALIGN_MULTILINE_ASSIGNMENT = false
      ALIGN_MULTILINE_BINARY_OPERATION = false
    }

    formatter.doTextTest("class Foo {\n" + "    void foo () {\n" + "String s = \"abc\" +\n" + "\"def\";" + "    }\n" + "}",

               "class Foo {\n" +
               "    void foo() {\n" +
               "        String s = \"abc\" +\n" +
               "                \"def\";\n" +
               "    }\n" +
               "}")

  }

  @Test
  fun test3() {
    formatter.doTest()
  }

  @Test
  fun test4() {
    formatter.lineRange = TextRange(2, 8)
    formatter.doTest()
  }

  @Test
  fun testBraces() {

    val text = "class Foo {\n" +
                       "void foo () {\n" +
                       "if (a) {\n" +
                       "int i = 0;\n" +
                       "}\n" +
                       "}\n" +
                       "}"

    formatter.settings.apply {
      BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    }
    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo() {\n" +
                     "        if (a) {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "    }\n" +
                     "}")

    formatter.settings.apply {
      BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    }
    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "    {\n" +
                     "        if (a)\n" +
                     "        {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "    }\n" +
                     "}")

    formatter.settings.apply {
      BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    }
    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "        if (a)\n" +
                     "            {\n" +
                     "            int i = 0;\n" +
                     "            }\n" +
                     "        }\n" +
                     "}")
    formatter.settings.apply {
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
      BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    }
    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "        if (a) {\n" +
                     "            int i = 0;\n" +
                     "        }\n" +
                     "        }\n" +
                     "}")

    formatter.settings.apply {
      METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
      BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    }
    formatter.doTextTest(text, "class Foo {\n" +
                     "    void foo()\n" +
                     "        {\n" +
                     "            if (a)\n" +
                     "                {\n" +
                     "                    int i = 0;\n" +
                     "                }\n" +
                     "        }\n" +
                     "}")

    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.doTextTest("class Foo {\n" + "    static{\n" + "foo();\n" + "}" + "}",
               "class Foo {\n" + "    static\n" + "    {\n" + "        foo();\n" + "    }\n" + "}")

  }

  @Test
  fun testExtendsList() {
    formatter.settings.ALIGN_MULTILINE_EXTENDS_LIST = true
    formatter.doTextTest("class A extends B, \n" + "C {}", "class A extends B,\n" + "                C {\n}")
  }

  @Test
  fun testBlockWithoutBraces() {
    formatter.doTextTest("class A {\n" + "void foo(){\n" + "if(a)\n" + "return;\n" + "}\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        if (a)\n" + "            return;\n" + "    }\n" + "}")
  }

  @Test
  fun testNestedCalls() {
    formatter.doTextTest("class A {\n" + "void foo(){\n" + "foo(\nfoo(\nfoo()\n)\n);\n" + "}\n" + "}", "class A {\n" +
                                                                                             "    void foo() {\n" +
                                                                                             "        foo(\n" +
                                                                                             "                foo(\n" +
                                                                                             "                        foo()\n" +
                                                                                             "                )\n" +
                                                                                             "        );\n" +
                                                                                             "    }\n" +
                                                                                             "}")

  }

  @Test
  fun testSpacesAroundMethod() {
    formatter.doTextTest("class Foo {\n" + "    abstract void a();\n" + "    {\n" + "        a();\n" + "    }\n" + "}",
               "class Foo {\n" + "    abstract void a();\n" + "\n" + "    {\n" + "        a();\n" + "    }\n" + "}")
  }

  @Test
  fun testSpaceInIf() {
    formatter.doTextTest("class foo {\n" +
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

  @Test
  fun testIf2() {
    formatter.doTextTest(
      "public class Test {\n" + "    public boolean equals(Object o) {\n" + "        if(this == o)return true;\n" + "    }\n" + "}",
      "public class Test {\n" + "    public boolean equals(Object o) {\n" + "        if (this == o) return true;\n" + "    }\n" + "}")
  }

  @Test
  fun testSpaceAroundField() {
    formatter.settings.BLANK_LINES_AROUND_FIELD = 1

    formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testArray() {
    formatter.settings.apply {
      SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = true
      SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true
    }
    formatter.doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{0};\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[] { 0 };\n" + "    }\n" + "}")
  }

  @Test
  fun testEmptyArray() {
    formatter.settings.apply {
      SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = true
      SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = true
      SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = false
    }
    formatter.doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{ };\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[] {};\n" + "    }\n" + "}")
  }

  @Test
  fun testEmptyArrayIsntWrapped() {
    formatter.settings.apply {
      ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true
      ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true
      SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = true
    }
    formatter.doTextTest("class a {\n" + " void f() {\n" + "   final int[] i = new int[]{ };\n" + " }\n" + "}",
               "class a {\n" + "    void f() {\n" + "        final int[] i = new int[]{ };\n" + "    }\n" + "}")
  }

  @Test
  fun testTwoJavaDocs() {
    formatter.doTextTest("""/**
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

  @Test
  fun testJavaDocLinksWithParameterNames() {
    // See IDEADEV-8332
    formatter.doTextTest("/**\n" +
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

  @Test
  fun testIncompleteField() {
    formatter.doTextTest("public class Test {\n" + "    String s =;\n" + "}", "public class Test {\n" + "    String s = ;\n" + "}")
  }

  @Test
  fun testIf3() {
    formatter.settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    formatter.doTextTest("public abstract class A {\n" +
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

  @Test
  fun testDocComment() {
    formatter.doTextTest("public class TestClass {\n" + "/**\n" + "* \n" + "*/\n" + "    public void f1() {\n" + "    }\n" + "}",
               "public class TestClass {\n" + "    /**\n" + "     *\n" + "     */\n" + "    public void f1() {\n" + "    }\n" + "}")
  }

  @Test
  fun testDocComment2() {
    formatter.settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    formatter.javaSettings.JD_KEEP_EMPTY_LINES = false
    formatter.doTextTest("class Test {\n" +
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

  @Test
  fun testSpaceBeforeFieldName() {
    formatter.doTextTest("class A{\n" + "public   A    myA ;\n" + "}", "class A {\n" + "    public A myA;\n" + "}")
  }

  @Test
  fun testClass() {
    formatter.doTextTest("    class A {\n" +
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

  @Test
  fun testDoNotIndentCaseFromSwitch() {
    formatter.settings.INDENT_CASE_FROM_SWITCH = false
    formatter.doTextTest("class A {\n" + "void foo() {\n" + "switch(a){\n" + "case 1: \n" + "break;\n" + "}\n" + "}\n" + "}", "class A {\n" +
                                                                                                                    "    void foo() {\n" +
                                                                                                                    "        switch (a) {\n" +
                                                                                                                    "        case 1:\n" +
                                                                                                                    "            break;\n" +
                                                                                                                    "        }\n" +
                                                                                                                    "    }\n" +
                                                                                                                    "}")
  }

  @Test
  fun testClass2() {
    formatter.settings.KEEP_FIRST_COLUMN_COMMENT = false
    formatter.doTextTest("class A {\n" + "// comment before\n" + "protected Object a;//  comment after\n" + "}",
               "class A {\n" + "    // comment before\n" + "    protected Object a;//  comment after\n" + "}")
  }

  @Test
  fun testSplitLiteral() {
    formatter.doTextTest("class A {\n" + "void foo() {\n" + "  String s = \"abc\" +\n" + "  \"def\";\n" + "}\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        String s = \"abc\" +\n" + "                \"def\";\n" + "    }\n" + "}")
  }

  @Test
  fun testParametersAlignment() {
    formatter.settings.apply {
      ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
      RIGHT_MARGIN = 140
    }
    formatter.doTest()
  }

  @Test
  fun testConditionalExpression() {
    formatter.settings.apply {
      SPACE_BEFORE_QUEST = true
      SPACE_AFTER_QUEST = false
      SPACE_BEFORE_COLON = true
      SPACE_AFTER_COLON = false
    }

    formatter.doTextTest("class Foo{\n" + "  void foo(){\n" + "  return name   !=   null   ?   1   :   2   ;" + "}\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        return name != null ?1 :2;\n" + "    }\n" + "}")
  }

  @Test
  fun testMethodCallChain() {
    formatter.doTextTest("class Foo{\n" +
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

    formatter.doTextTest("class Foo{\n" +
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

    formatter.doTextTest("class Foo{\n" +
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

  @Test
  fun testComment1() {
    formatter.doTextTest(
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

  @Test
  fun testElseAfterComment() {
    formatter.doTextTest("public class Foo {\n" +
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

  @Test
  fun testLBraceAfterComment() {
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.doTextTest("public class Foo {\n" +
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

  @Test
  fun testSpaces() {
    val settings = formatter.settings
    settings.SPACE_WITHIN_FOR_PARENTHESES = true
    settings.SPACE_WITHIN_IF_PARENTHESES = true
    settings.SPACE_WITHIN_METHOD_PARENTHESES = true
    settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    settings.SPACE_BEFORE_METHOD_PARENTHESES = true
    settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    formatter.doTest()
  }

  @Test
  fun testSpacesBeforeLBrace() {
    val settings = formatter.settings
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

    formatter.doTest()

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

    formatter.doTest("SpacesBeforeLBrace.java", "SpacesBeforeLBrace.java")
  }

  @Test
  fun testCommentBeforeField() {
    val settings = formatter.settings
    settings.KEEP_LINE_BREAKS = false
    settings.KEEP_FIRST_COLUMN_COMMENT = false
    settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false
    formatter.doTextTest("class Foo{\n" + "    //Foo a\n" + "    Foo a; \n" + "}", "class Foo {\n" + "    //Foo a\n" + "    Foo a;\n" + "}")
  }

  @Test
  fun testLabel() {
    val settings = formatter.settings
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).LABEL_INDENT_ABSOLUTE = true
    settings.SPECIAL_ELSE_IF_TREATMENT = true
    settings.FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    formatter.textRange = TextRange(59, 121)
    formatter.doTextTest("public class Foo {\n" +
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

  @Test
  fun testElseOnNewLine() {
    formatter.doTextTest("class Foo{\n" + "void foo() {\n" + "if (a)\n" + "return;\n" + "else\n" + "return;\n" + "}\n" + "}", "class Foo {\n" +
                                                                                                                    "    void foo() {\n" +
                                                                                                                    "        if (a)\n" +
                                                                                                                    "            return;\n" +
                                                                                                                    "        else\n" +
                                                                                                                    "            return;\n" +
                                                                                                                    "    }\n" +
                                                                                                                    "}")
  }

  @Test
  fun testTwoClasses() {
    formatter.doTextTest("class A {}\n" + "class B {}", "class A {\n" + "}\n" + "\n" + "class B {\n" + "}")
  }

  @Test
  fun testBraceOnNewLineIfWrapped() {
    formatter.settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    formatter.settings.RIGHT_MARGIN = 35
    formatter.settings.ALIGN_MULTILINE_BINARY_OPERATION = true

    formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testFirstArgumentWrapping() {
    formatter.settings.RIGHT_MARGIN = 20
    formatter.settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(1);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(\n" + "                1);\n" + "    }\n" + "}")

    formatter.settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFooFoo(1,2);" + "    }\n" + "}", "class Foo {\n" +
                                                                                                        "    void foo() {\n" +
                                                                                                        "        fooFooFooFoo(\n" +
                                                                                                        "                1,\n" +
                                                                                                        "                2);\n" +
                                                                                                        "    }\n" +
                                                                                                        "}")

    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        fooFooFoo(1,2);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        fooFooFoo(1,\n" + "                2);\n" + "    }\n" + "}")

  }

  @Test
  fun testSpacesInsideWhile() {
    formatter.settings.SPACE_WITHIN_WHILE_PARENTHESES = true
    formatter.doTextTest("class Foo{\n" + "    void foo() {\n" + "        while(x != y) {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        while ( x != y ) {\n" + "        }\n" + "    }\n" + "}")
  }

  @Test
  fun testAssertStatementWrapping() {
    formatter.settings.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    formatter.settings.RIGHT_MARGIN = 40
    val facade = JavaPsiFacade.getInstance(formatter.project)
    val effectiveLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.project).languageLevel
    try {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)

      formatter.settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false
      formatter.doTextTest("class Foo {\n" +
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

      formatter.settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = true
      formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testAssertStatementWrapping2() {
    formatter.settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    formatter.settings.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.RIGHT_MARGIN = 37

    val options = formatter.settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE)
    options.INDENT_SIZE = 2
    options.CONTINUATION_INDENT_SIZE = 2

    formatter.settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = true

    val facade = JavaPsiFacade.getInstance(formatter.project)
    val effectiveLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)

    try {
      formatter.doTextTest(
        "class Foo {\n" + "    void foo() {\n" + "        assert i + j + k + l + n + m <= 2 : \"assert description\";" + "    }\n" + "}",
        "class Foo {\n" +
        "  void foo() {\n" +
        "    assert i + j + k + l + n + m <= 2\n" +
        "      : \"assert description\";\n" +
        "  }\n" +
        "}")

      formatter.settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false

      formatter.doTextTest(
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

  @Test
  fun test() {
    formatter.settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 2
    formatter.settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 2
    formatter.settings.RIGHT_MARGIN = 37
    formatter.settings.ALIGN_MULTILINE_EXTENDS_LIST = true

    formatter.settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED

    formatter.settings.ASSERT_STATEMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false
    formatter.settings.ALIGN_MULTILINE_BINARY_OPERATION = true

    val facade = JavaPsiFacade.getInstance(formatter.project)
    val effectiveLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)


    try {
      formatter.doTextTest("public class ThisIsASampleClass extends C1 implements I1, I2, I3, I4, I5 {\n" +
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

  @Test
  fun testLBrace() {
    formatter.settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.settings.RIGHT_MARGIN = 14
    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        \n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "\n" + "    }\n" + "}")
  }

  @Test
  fun testJavaDocLeadingAsterisksAreDisabled() {
    formatter.javaSettings.JD_LEADING_ASTERISKS_ARE_ENABLED = false
    formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testBinaryExpression() {
    formatter.settings.ALIGN_MULTILINE_BINARY_OPERATION = true
    formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testCaseBraces() {
    formatter.doTextTest("class Foo{\n" +
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

  @Test
  fun testFormatCodeFragment(): Unit = timeoutRunBlocking {
    val factory = JavaCodeFragmentFactory.getInstance(formatter.project)
    val fragment = readAction { factory.createCodeBlockCodeFragment("a=1;int b=2;", null, true) }
    val result = arrayOfNulls<PsiElement>(1)

    withContext(Dispatchers.EDT) {
      CommandProcessor.getInstance().executeCommand(formatter.project, {
        WriteCommandAction.runWriteCommandAction(null) {
          try {
            result[0] = CodeStyleManager.getInstance(formatter.project).reformat(fragment)
          }
          catch (e: IncorrectOperationException) {
            fail(e.localizedMessage)
          }
        }
      }, null, null)
    }

    assertEquals("a = 1;\n" + "int b = 2;", readAction { result[0]!!.text })
  }

  @Test
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

    formatter.doTextTest(before, after)
  }

  @Test
  fun testArrayInitializerWrapping() {
    formatter.settings.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false
    formatter.settings.RIGHT_MARGIN = 37

    formatter.doTextTest("class Foo{\n" +
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

    formatter.settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true

    formatter.doTextTest("class Foo{\n" +
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

  @Test
  fun testJavaDocIndentation() {
    formatter.settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 2
    formatter.settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 2
    formatter.settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 4

    formatter.javaSettings.ENABLE_JAVADOC_FORMATTING = false

    formatter.doTextTest("public interface PsiParser {\n" +
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

  @Test
  fun testRemoveLineBreak() {
    formatter.settings.KEEP_LINE_BREAKS = true
    formatter.settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE

    formatter.doTextTest("class A\n" + "{\n" + "}", "class A {\n" + "}")

    formatter.doTextTest("class A {\n" + "    void foo()\n" + "    {\n" + "    }\n" + "}", "class A {\n" + "    void foo() {\n" + "    }\n" + "}")

    formatter.doTextTest("class A {\n" + "    void foo()\n" + "    {\n" + "        if (a)\n" + "        {\n" + "        }\n" + "    }\n" + "}",
               "class A {\n" + "    void foo() {\n" + "        if (a) {\n" + "        }\n" + "    }\n" + "}")

  }

  private fun setUpImportLayoutWithJavaGroup() {
    formatter.javaSettings.IMPORT_LAYOUT_TABLE = PackageEntryTable().apply {
      addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY)
      addEntry(PackageEntry.BLANK_LINE_ENTRY)
      addEntry(PackageEntry(false, "java", true))
      addEntry(PackageEntry(false, "javax", true))
      addEntry(PackageEntry.BLANK_LINE_ENTRY)
      addEntry(PackageEntry.BLANK_LINE_ENTRY)
      addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY)
    }
  }

  private val importsWithExtraBlankLines = """
package com.example;

import java.util.Optional;


import java.util.Set;



import com.custom.MyClass1;

import com.custom.MyClass2;

class Foo {
}"""

  @Test
  fun testExtraBlankLinesBetweenImportsAreKeptByDefault() {
    setUpImportLayoutWithJavaGroup()
    formatter.javaSettings.KEEP_BLANK_LINES_BETWEEN_IMPORTS = true
    formatter.doTextTest(importsWithExtraBlankLines,
               """
package com.example;

import java.util.Optional;


import java.util.Set;


import com.custom.MyClass1;

import com.custom.MyClass2;

class Foo {
}""")
  }

  @Test
  fun testExtraBlankLinesBetweenImportsRemovedWhenLayoutIsAuthoritative() {
    setUpImportLayoutWithJavaGroup()
    formatter.javaSettings.KEEP_BLANK_LINES_BETWEEN_IMPORTS = false

    formatter.doTextTest(importsWithExtraBlankLines,
               """
package com.example;

import java.util.Optional;
import java.util.Set;


import com.custom.MyClass1;
import com.custom.MyClass2;

class Foo {
}""")
  }

  @Test
  fun testBlankLines() {
    formatter.settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
    formatter.settings.KEEP_BLANK_LINES_IN_CODE = 0
    formatter.settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0
    formatter.settings.BLANK_LINES_AFTER_CLASS_HEADER = 0
    formatter.settings.BLANK_LINES_AFTER_IMPORTS = 0
    formatter.settings.BLANK_LINES_AFTER_PACKAGE = 0
    formatter.settings.BLANK_LINES_AROUND_CLASS = 0
    formatter.settings.BLANK_LINES_AROUND_FIELD = 0
    formatter.settings.BLANK_LINES_AROUND_METHOD = 0
    formatter.settings.BLANK_LINES_BEFORE_IMPORTS = 0
    formatter.settings.BLANK_LINES_BEFORE_PACKAGE = 0
    formatter.settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 2
    formatter.settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 3

    formatter.doTextTest("/*\n" +
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

    formatter.doTextTest("interface Foo {\n" +
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

  @Test
  fun testStaticBlockBraces() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}")

    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    formatter.doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}")

    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    static\n" + "    {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}")


    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    formatter.doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "        }\n" + "}",
               "class Foo {\n" + "    static\n" + "        {\n" + "        //comment\n" + "        i = foo();\n" + "        }\n" + "}")


    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    formatter.doTextTest("class Foo {\n" + "    static {\n" + "        //comment\n" + "        i = foo();\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                      "    static\n" +
                                                                                                                      "        {\n" +
                                                                                                                      "            //comment\n" +
                                                                                                                      "            i = foo();\n" +
                                                                                                                      "        }\n" +
                                                                                                                      "}")


  }

  @Test
  fun testBraces2() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    formatter.doTextTest("class Foo {\n" +
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

    formatter.doTextTest("class Foo {\n" +
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


    formatter.doTextTest("class Foo {\n" +
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


    formatter.doTextTest("class Foo {\n" +
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

    formatter.settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED

    formatter.doTextTest("class Foo{\n" + "    /**\n" + "     *\n" + "     */\n" + "    void foo() {\n" + "    }\n" + "}",
               "class Foo {\n" + "    /**\n" + "     *\n" + "     */\n" + "    void foo() {\n" + "    }\n" + "}")


    formatter.settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED

    formatter.doTextTest("/**\n" + " *\n" + " */\n" + "class Foo\n{\n" + "}", "/**\n" + " *\n" + " */\n" + "class Foo {\n" + "}")

    formatter.doTextTest("/**\n" + " *\n" + " */\n" + "class Foo\n extends B\n{\n" + "}",
               "/**\n" + " *\n" + " */\n" + "class Foo\n        extends B\n" + "{\n" + "}")

    formatter.doTextTest("/**\n" + " *\n" + " */\n" + "class Foo\n permits B\n{\n" + "}",
               "/**\n" + " *\n" + " */\n" + "class Foo\n        permits B\n" + "{\n" + "}")

  }

  @Test
  fun testSynchronized() {

    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this) {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "        }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}")

    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this)\n" +
                                                                                                                   "        {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "        }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}")

    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                   "    void foo() {\n" +
                                                                                                                   "        synchronized (this)\n" +
                                                                                                                   "            {\n" +
                                                                                                                   "            foo();\n" +
                                                                                                                   "            }\n" +
                                                                                                                   "    }\n" +
                                                                                                                   "}")


    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "synchronized (this) {\n" + "foo();\n" + "}\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                          "    void foo() {\n" +
                                                                                                                          "        synchronized (this)\n" +
                                                                                                                          "            {\n" +
                                                                                                                          "                foo();\n" +
                                                                                                                          "            }\n" +
                                                                                                                          "    }\n" +
                                                                                                                          "}")

  }

  @Test
  fun testNextLineShiftedForBlockStatement() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED

    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a)\n" + "        foo();\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        if (a)\n" + "            foo();\n" + "    }\n" + "}")
  }

  @Test
  fun testFieldWithJavadocAndAnnotation() {
    formatter.doTextTest("class Foo {\n" + "    /**\n" + "     * java doc\n" + "     */\n" + "    @NoInspection\n" + "    String field;\n" + "}",
               "class Foo {\n" + "    /**\n" + "     * java doc\n" + "     */\n" + "    @NoInspection\n" + "    String field;\n" + "}")
  }

  @Test
  fun testLongCallChainAfterElse() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true
    formatter.settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    formatter.settings.ELSE_ON_NEW_LINE = false
    formatter.settings.RIGHT_MARGIN = 110
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testSpacesIncode() {

    val facade = JavaPsiFacade.getInstance(formatter.project)
    val level = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)

    try {
      formatter.doTextTest("class C<Y, X> {\n" + "}", "class C<Y, X> {\n" + "}")

      formatter.settings.SPACE_BEFORE_METHOD_LBRACE = true
      formatter.settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true

      formatter.doTextTest("enum En {\n" + "    A(10) {},\n" + "    B(10) {},\n" + "    C(10);\n" + "\n" + "    En(int i) { }\n" + "}",
                 "enum En {\n" + "    A(10) {},\n" + "    B(10) {},\n" + "    C(10);\n" + "\n" + "    En(int i) {}\n" + "}")

      formatter.doTextTest("class C {\n" +
                 "    void foo (Map<?, String> s) {\n" +
                 "        Set<? extends Map<?, String>.Entry<?, String>> temp = s.entries();\n" +
                 "    }\n" +
                 "}", "class C {\n" +
                      "    void foo(Map<?, String> s) {\n" +
                      "        Set<? extends Map<?, String>.Entry<?, String>> temp = s.entries();\n" +
                      "    }\n" +
                      "}")

      formatter.doTextTest("class B {\n" +
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
  @Test
  fun testKeepBlankLineInCodeBeforeComment() {
    formatter.settings.KEEP_BLANK_LINES_IN_CODE = 1
    formatter.settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
    formatter.settings.KEEP_FIRST_COLUMN_COMMENT = false

    formatter.doTextTest("public class ReformatProblem {\n" +
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

    formatter.settings.KEEP_BLANK_LINES_IN_CODE = 0
    formatter.settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 1
    formatter.settings.KEEP_FIRST_COLUMN_COMMENT = false

    formatter.doTextTest("public class ReformatProblem {\n" +
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

  @Test
  fun testSpaceBeforeTryBrace() {
    formatter.settings.SPACE_BEFORE_TRY_LBRACE = false
    formatter.settings.SPACE_BEFORE_FINALLY_LBRACE = true
    formatter.doTextTest("class Foo{\n" + "    void foo() {\n" + "        try {\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        try{\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}")

    formatter.settings.SPACE_BEFORE_TRY_LBRACE = true
    formatter.settings.SPACE_BEFORE_FINALLY_LBRACE = false

    formatter.doTextTest("class Foo{\n" + "    void foo() {\n" + "        try {\n" + "        } finally {\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        try {\n" + "        } finally{\n" + "        }\n" + "    }\n" + "}")

  }

  @Test
  fun testFormatComments() {
    formatter.javaSettings.ENABLE_JAVADOC_FORMATTING = true
    formatter.doTextTest("public class Test {\n" + "\n" + "    /**\n" + "     * The s property.\n" + "     */\n" + "    private String s;\n" + "}",
               "public class Test {\n" + "\n" + "    /**\n" + "     * The s property.\n" + "     */\n" + "    private String s;\n" + "}")

  }

  @Throws(IncorrectOperationException::class)
  @Test
  fun testDoNotWrapLBrace() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.settings.RIGHT_MARGIN = 66
    formatter.doTextTest("public class Test {\n" +
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
  @Test
  fun testNewLinesAroundArrayInitializer() {
    formatter.settings.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = true
    formatter.settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = true
    formatter.settings.RIGHT_MARGIN = 40
    formatter.doTextTest("class Foo {\n" + "    int[] a = new int[]{1,2,0x0052,0x0053,0x0054,0x0054,0x0054};\n" + "}", "class Foo {\n" +
                                                                                                             "    int[] a = new int[]{\n" +
                                                                                                             "            1, 2, 0x0052, 0x0053,\n" +
                                                                                                             "            0x0054, 0x0054, 0x0054\n" +
                                                                                                             "    };\n" +
                                                                                                             "}")
  }

  @Throws(IncorrectOperationException::class)
  @Test
  fun testSpaceAfterCommaInEnum() {
    formatter.settings.SPACE_AFTER_COMMA = true

    val facade = JavaPsiFacade.getInstance(formatter.project)
    val effectiveLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)
    try {
      formatter.doTextTest("public enum StringExDirection {\n" + "\n" + "    RIGHT_TO_LEFT, LEFT_TO_RIGHT\n" + "\n" + "}",
                 "public enum StringExDirection {\n" + "\n" + "    RIGHT_TO_LEFT, LEFT_TO_RIGHT\n" + "\n" + "}")
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, effectiveLanguageLevel)
    }
  }

  @Test
  fun testWrapBetweenCommaAndSemicolon() {
      formatter.doTextTest("""
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
  @Test
  fun testRemoveBraceBeforeInstanceOf() {
    formatter.doTextTest("class ReformatInstanceOf {\n" +
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
  @Test
  fun testAnnotationBeforePackageLocalConstructor() {
    formatter.doTextTest("class Foo {\n" + "    @MyAnnotation Foo() {\n" + "    }\n" + "}",
               "class Foo {\n" + "    @MyAnnotation\n" + "    Foo() {\n" + "    }\n" + "}")
  }

  @Test
  fun testLongAnnotationsAreNotWrapped() {
    formatter.settings.ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.doTest()
  }

  @Test
  fun testWrapExtendsList() {
    formatter.settings.RIGHT_MARGIN = 50
    formatter.settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    formatter.settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED

    formatter.doTextTest("class ColtreDataProvider extends DataProvider, AgentEventListener, ParameterDataEventListener {\n}",
               "class ColtreDataProvider extends DataProvider,\n" +
               "        AgentEventListener,\n" +
               "        ParameterDataEventListener {\n}")
  }

  @Test
  fun testWrapLongExpression() {
    formatter.settings.RIGHT_MARGIN = 80
    formatter.settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.ALIGN_MULTILINE_BINARY_OPERATION = true
    formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testDoNotWrapCallChainIfParametersWrapped() {
    formatter.settings.RIGHT_MARGIN = 87
    formatter.settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    //getSettings().PREFER_PARAMETERS_WRAP = true;

    formatter.doMethodTest(
      //9                    30                            70         80    86
      "descriptors = manager.createProblemDescriptor(parameter1, parameter2, parameterparameterpar3,parameter4);",

      "descriptors = manager.createProblemDescriptor(parameter1, parameter2,\n" +
      "                                              parameterparameterpar3,\n" +
      "                                              parameter4);"

    )
  }

  @Test
  fun testAlignTernaryOperation() {
    formatter.settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    formatter.doMethodTest("String s = x == 0 ? \"hello\" :\n" +
                 "                x == 5 ? \"something else\" :\n" +
                 "                        x > 0 ? \"bla, bla\" :\n" +
                 "                                \"\";", "String s = x == 0 ? \"hello\" :\n" +
                                                          "           x == 5 ? \"something else\" :\n" +
                                                          "           x > 0 ? \"bla, bla\" :\n" +
                                                          "           \"\";")

    formatter.settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = true

    formatter.doMethodTest("int someVariable = a ?\n" + "x :\n" + "y;",
                 "int someVariable = a ?\n" + "                   x :\n" + "                   y;")
  }

  @Test
  fun testRightMargin_2() {
    formatter.settings.RIGHT_MARGIN = 65
    formatter.settings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = true
    formatter.settings.KEEP_LINE_BREAKS = false

    formatter.doClassTest(
      "public static final Map<LongType, LongType> longVariableName =\n" + "variableValue;",
      "public static final Map<LongType, LongType> longVariableName\n" + "        = variableValue;")
  }

  @Test
  fun testRightMargin_3() {
    formatter.settings.RIGHT_MARGIN = 65
    formatter.settings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false
    formatter.settings.KEEP_LINE_BREAKS = false

    formatter.doClassTest(
      "public static final Map<LongType, LongType> longVariableName =\n" + "variableValue;",
      "public static final Map<LongType, LongType>\n" + "        longVariableName = variableValue;")
  }

  @Test
  fun testDoNotRemoveLineBreaksBetweenComments() {
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.settings.KEEP_FIRST_COLUMN_COMMENT = false

    formatter.doTextTest(
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

  @Test
  fun testWrapParamsOnEveryItem() {
    val codeStyleSettings = CodeStyle.getSettings(formatter.project)

    val javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE)
    val oldMargin = javaSettings.RIGHT_MARGIN
    val oldKeep = javaSettings.KEEP_LINE_BREAKS
    val oldWrap = javaSettings.METHOD_PARAMETERS_WRAP

    try {
      codeStyleSettings.setRightMargin(JavaLanguage.INSTANCE, 80)
      javaSettings.KEEP_LINE_BREAKS = false
      javaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM

      formatter.doClassTest(
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

  @Test
  fun testCommentAfterDeclaration() {
    val codeStyleSettings = CodeStyle.getSettings(formatter.project)
    val javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE)

    val oldWrap = javaSettings.ASSIGNMENT_WRAP

    try {
      codeStyleSettings.defaultRightMargin = 20
      javaSettings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
      formatter.doMethodTest(
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

  @Test
  fun testSCR915() {
    formatter.settings.SPACE_AROUND_ADDITIVE_OPERATORS = false
    formatter.doTest("SCR915.java", "SCR915_after.java")
  }

  @Test
  fun testSCR429() {
    val settings = formatter.settings
    settings.KEEP_BLANK_LINES_IN_CODE = 2
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 2
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 2
    formatter.doTest()
  }

  @Test
  fun testSCR548() {
    val settings = formatter.settings
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).INDENT_SIZE = 4
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 2
    formatter.doTest()
  }

  @Test
  fun testIDEA_16151() {
    formatter.doClassTest("@ValidateNestedProperties({\n" +
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

  @Test
  fun testIDEA_14324() {
    formatter.settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true

    formatter.doClassTest(
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

    formatter.doClassTest("@AttributeOverrides({ @AttributeOverride(name = \"address\", column = @Column(name = \"ADDR\")),\n" +
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

  @Test
  fun testSCR260() {
    val settings = formatter.settings
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    settings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    settings.KEEP_LINE_BREAKS = false
    formatter.doTest()
  }

  @Test
  fun testSCR114() {
    val settings = formatter.settings
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.CATCH_ON_NEW_LINE = true
    formatter.doTest()
  }

  @Test
  fun testSCR259() {
    formatter.textRange = TextRange(36, 60)
    val settings = formatter.settings
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    settings.KEEP_LINE_BREAKS = false
    formatter.doTest()
  }

  @Test
  fun testSCR279() {
    val settings = formatter.settings
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true
    formatter.doTest()
  }

  @Test
  fun testSCR395() {
    val settings = formatter.settings
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.doTest()
  }

  @Test
  fun testSCR11799() {
    val settings = formatter.settings
    settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE).CONTINUATION_INDENT_SIZE = 4
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.doTest()
  }

  @Test
  fun testSCR501() {
    val settings = formatter.settings
    settings.KEEP_FIRST_COLUMN_COMMENT = true
    formatter.doTest()
  }

  @Test
  fun testSCR879() {
    val settings = formatter.settings
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.doTest()
  }


  @Test
  fun testSCR547() {
    formatter.doTextTest("class Foo { \n" +
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

  @Test
  fun testSCR479() {
    val settings = formatter.settings
    settings.RIGHT_MARGIN = 80
    settings.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.doTextTest("public class Foo {\n" +
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

  @Test
  fun testSCR190() {
    val settings = formatter.settings
    settings.KEEP_LINE_BREAKS = false
    formatter.doTextTest("public class EntityObject \n" +
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

  @Test
  fun testSCR1535() {
    val settings = formatter.settings
    settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.doTextTest("public class Foo {\n" +
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

  @Test
  fun testSCR970() {
    val settings = formatter.settings
    settings.THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    settings.THROWS_LIST_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.doTest()
  }

  @Test
  fun testSCR1486() {
    formatter.doTextTest("public class Test {\n" + "  private BigDecimal\n" + "}", "public class Test {\n" + "    private BigDecimal\n" + "}")

    formatter.doTextTest("public class Test {\n" + "  @NotNull private BigDecimal\n" + "}",
               "public class Test {\n" + "    @NotNull\n" + "    private BigDecimal\n" + "}")

  }

  @Test
  fun test1607() {
    formatter.settings.RIGHT_MARGIN = 30
    formatter.settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    formatter.settings.ALIGN_MULTILINE_PARAMETERS = true
    formatter.settings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.doTextTest("class TEst {\n" + "void foo(A a,B b){ /* compiled code */ }\n" + "}",
               "class TEst {\n" + "    void foo(A a, B b)\n" + "    { /* compiled code */ }\n" + "}")
  }

  @Test
  fun testSCR1615() {
    formatter.settings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    formatter.settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED

    formatter.doTextTest(
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

  @Test
  fun testSCR1047() {
    formatter.doTextTest("class Foo{\n" + "    void foo(){\n" + "        String field1, field2;\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        String field1, field2;\n" + "    }\n" + "}")
  }

  @Test
  fun testSCR524() {
    formatter.settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    formatter.settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    formatter.settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    formatter.doTextTest("class Foo {\n" + "    void foo() { return;}" + "}", "class Foo {\n" + "    void foo() {return;}\n" + "}")

    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED2
    formatter.settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false
    formatter.settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
    formatter.settings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE
    formatter.settings.CASE_STATEMENT_ON_NEW_LINE = false

    formatter.doTextTest("class Foo{\n" +
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

  @Test
  fun testSCR3062() {
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    formatter.settings.RIGHT_MARGIN = 80

    formatter.settings.PREFER_PARAMETERS_WRAP = true

    formatter.doTextTest(
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

    formatter.settings.PREFER_PARAMETERS_WRAP = false

    formatter.doTextTest(
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

  @Test
  fun testSCR1658() {
    formatter.doTextTest("/** \n" + " * @author\tMike\n" + " */\n" + "public class Foo {\n" + "}",
               "/**\n" + " * @author Mike\n" + " */\n" + "public class Foo {\n" + "}")
  }

  @Test
  fun testSCR1699() {
    formatter.doTextTest("class Test {\n" + "    Test(String t1 , String t2) {\n" + "    }\n" + "}",
               "class Test {\n" + "    Test(String t1, String t2) {\n" + "    }\n" + "}")
  }

  @Test
  fun testSCR1700() {
    formatter.doTextTest("class Test {\n" + "    Test(String      t1 , String      t2) {\n" + "    }\n" + "}",
               "class Test {\n" + "    Test(String t1, String t2) {\n" + "    }\n" + "}")
  }

  @Test
  fun testSCR1701() {
    formatter.settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    formatter.settings.SPACE_WITHIN_METHOD_PARENTHESES = false
    formatter.settings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    formatter.settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    formatter.settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        foo(a,b);" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        foo( a, b );\n" + "    }\n" + "}")
  }

  @Test
  fun testSCR1703() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    formatter.doTextTest("class Foo{\n" +
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

  @Test
  fun testSCR1804() {
    formatter.settings.ALIGN_MULTILINE_ASSIGNMENT = true
    formatter.doTextTest(
      "class Foo {\n" + "    void foo() {\n" + "        int i;\n" + "        i = \n" + "                1 + 2;\n" + "    }\n" + "}",
      "class Foo {\n" + "    void foo() {\n" + "        int i;\n" + "        i =\n" + "                1 + 2;\n" + "    }\n" + "}")

    formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        i = j =\n" + "        k = l = 1 + 2;\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        i = j =\n" + "        k = l = 1 + 2;\n" + "    }\n" + "}")

  }

  @Test
  fun testSCR1795() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    formatter.doTextTest("public class Test {\n" +
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

  @Test
  fun testSCR1936() {
    formatter.settings.BLANK_LINES_AFTER_CLASS_HEADER = 4
    formatter.doTextTest("/**\n" + " * Foo - test class\n" + " */\n" + "class Foo{\n" + "}",
               "/**\n" + " * Foo - test class\n" + " */\n" + "class Foo {\n" + "\n" + "\n" + "\n" + "\n" + "}")

    formatter.doTextTest("/**\n" + " * Foo - test class\n" + " */\n" + "class Foo{\n" + "    int myFoo;\n" + "}",
               "/**\n" + " * Foo - test class\n" + " */\n" + "class Foo {\n" + "\n" + "\n" + "\n" + "\n" + "    int myFoo;\n" + "}")

  }

  @Test
  fun test1980() {
    formatter.settings.RIGHT_MARGIN = 144
    formatter.settings.TERNARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    formatter.settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    formatter.settings.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = true
    formatter.doTextTest("class Foo{\n" +
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

  @Test
  fun testSCR2089() {
    formatter.doTextTest("class Test { \n" +
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

  @Test
  fun testSCR2132() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED
    formatter.settings.ELSE_ON_NEW_LINE = true

    formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testIDEADEV1047() {
    formatter.doTextTest("class Foo{\n" + "String field1\n" + ",\n" + "field2\n" + ";" + "}",
               "class Foo {\n" + "    String field1,\n" + "            field2;\n" + "}")

    formatter.doTextTest("class Foo{\n" + "void foo() {\n" + "    String var1\n" + ",\n" + "var2\n" + ";\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        String var1,\n" + "                var2;\n" + "    }\n" + "}")

  }

  @Test
  fun testIDEADEV1047_2() {
    formatter.doTextTest(
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

  @Test
  fun testSCR2241() {
    formatter.settings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    formatter.settings.SPECIAL_ELSE_IF_TREATMENT = true
    formatter.settings.ELSE_ON_NEW_LINE = true
    formatter.doTextTest("class Foo {\n" +
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
  @Test
  fun testSCRIDEA_4783() {
    formatter.settings.ASSIGNMENT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    formatter.settings.RIGHT_MARGIN = 80

    formatter.doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        final CommandRouterProtocolHandler protocolHandler =\n" +
                    "                (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "    }\n" +
                    "}")


    formatter.doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        protocolHandlerCommandRouterProtocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        protocolHandlerCommandRouterProtocolHandler =\n" +
                    "                (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "    }\n" +
                    "}")

    formatter.doTextTest("class Foo{\n" +
               "    final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "}", "class Foo {\n" +
                    "    final CommandRouterProtocolHandler protocolHandler =\n" +
                    "            (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "}")

    formatter.settings.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = true

    formatter.doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        final CommandRouterProtocolHandler protocolHandler\n" +
                    "                = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "    }\n" +
                    "}")

    formatter.doTextTest("class Foo{\n" +
               "    void foo() {\n" +
               "        protocolHandlerCommandRouterProtocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "    }\n" +
               "}", "class Foo {\n" +
                    "    void foo() {\n" +
                    "        protocolHandlerCommandRouterProtocolHandler\n" +
                    "                = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "    }\n" +
                    "}")


    formatter.doTextTest("class Foo{\n" +
               "    final CommandRouterProtocolHandler protocolHandler = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
               "}", "class Foo {\n" +
                    "    final CommandRouterProtocolHandler protocolHandler\n" +
                    "            = (CommandRouterProtocolHandler) connection.getProtocolHandler()\n" +
                    "}")

  }

  @Throws(IncorrectOperationException::class)
  @Test
  fun testSCRIDEADEV_2292() {
    formatter.settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    formatter.settings.WHILE_ON_NEW_LINE = true

    val facade = JavaPsiFacade.getInstance(formatter.project)
    val stored = IdeaTestUtil.setProjectLanguageLevel(facade.project, LanguageLevel.JDK_1_5)

    try {
      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        if (a)\n" +
                 "            foo();\n" +
                 "        else\n" +
                 "            bar();\n" +
                 "    }\n" +
                 "}")


      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        for (int i = 0; i < 10; i++)\n" +
                 "            foo();\n" +
                 "    }\n" +
                 "}")


      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars)\n" + "            foo();\n" + "    }\n" + "}")


      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                 "    void foo() {\n" +
                                                                                                                 "        do\n" +
                                                                                                                 "            foo();\n" +
                                                                                                                 "        while (true);\n" +
                                                                                                                 "    }\n" +
                                                                                                                 "}")


      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true)\n" + "            foo();\n" + "    }\n" + "}")

      formatter.settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true
      formatter.settings.WHILE_ON_NEW_LINE = false

      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}")

      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}")


      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}")


      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}")


      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true) foo();\n" + "    }\n" + "}")

      formatter.settings.RIGHT_MARGIN = 17

      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        if (a) foo();\n" + "        else bar();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        if (a)\n" +
                 "            foo();\n" +
                 "        else\n" +
                 "            bar();\n" +
                 "    }\n" +
                 "}")

      formatter.settings.RIGHT_MARGIN = 30

      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int i = 0; i < 10; i++) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" +
                 "    void foo() {\n" +
                 "        for (int i = 0; i < 10; i++)\n" +
                 "            foo();\n" +
                 "    }\n" +
                 "}")

      formatter.settings.RIGHT_MARGIN = 32
      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        for (int var : vars) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        for (int var : vars)\n" + "            foo();\n" + "    }\n" + "}")


      formatter.settings.RIGHT_MARGIN = 12
      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        do foo(); while (true);\n" + "    }\n" + "}", "class Foo {\n" +
                                                                                                                 "    void foo() {\n" +
                                                                                                                 "        do\n" +
                                                                                                                 "            foo();\n" +
                                                                                                                 "        while (true);\n" +
                                                                                                                 "    }\n" +
                                                                                                                 "}")

      formatter.settings.RIGHT_MARGIN = 23

      formatter.doTextTest("class Foo {\n" + "    void foo() {\n" + "        while(true) foo();\n" + "    }\n" + "}",
                 "class Foo {\n" + "    void foo() {\n" + "        while (true)\n" + "            foo();\n" + "    }\n" + "}")

    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(facade.project, stored)
    }


  }

  @Test
  fun testSCR3115() {
    val indentOptions = formatter.settings.rootSettings.getIndentOptions(JavaFileType.INSTANCE)
    indentOptions.USE_TAB_CHARACTER = true
    indentOptions.SMART_TABS = true

    formatter.settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true

    formatter.doTextTest("class Foo {\n" +
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

  @Test
  fun testIDEADEV_6239() {
    formatter.javaSettings.ENABLE_JAVADOC_FORMATTING = true
    formatter.doTextTest("public class Test {\n" +
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
  @Test
  fun testIDEADEV_8755() {
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.doTextTest("class Foo {\n" +
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
  @Test
  fun testIDEADEV_24168() {
    formatter.doTextTest(
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
  @Test
  fun testIDEADEV_2541() {
    formatter.textRange = TextRange(0, 15)
    formatter.doTextTest("/** @param q */\nclass Foo {\n}", "/**\n" + " * @param q\n" + " */\n" + "class Foo {\n" + "}")
  }

  @Throws(IncorrectOperationException::class)
  @Test
  fun testIDEADEV_6434() {
    formatter.settings.ALIGN_MULTILINE_BINARY_OPERATION = true
    formatter.settings.ALIGN_MULTILINE_ASSIGNMENT = true
    formatter.doTextTest("class Foo {\n" +
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
  @Test
  fun testIDEADEV_12836() {
    formatter.settings.SPECIAL_ELSE_IF_TREATMENT = true
    formatter.settings.RIGHT_MARGIN = 80
    formatter.doTextTest("class Foo {\n" +
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
  @Test
  fun test23551() {
    formatter.doTextTest("public class Wrapping {\n" +
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
  @Test
  fun testIDEADEV_23551() {
    formatter.settings.BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM

    formatter.settings.RIGHT_MARGIN = 60
    formatter.doTextTest("public class Wrapping {\n" +
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
  @Test
  fun testIDEADEV_22967() {
    formatter.settings.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    formatter.doTextTest("public interface TestInterface {\n" +
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
  @Test
  fun testIDEADEV_22967_2() {
    formatter.settings.METHOD_ANNOTATION_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    formatter.doTextTest("public interface TestInterface {\n" + "    @Deprecated\n" + "    <T> void parametrizedAnnotated(T data);\n" + "}",
               "public interface TestInterface {\n" + "    @Deprecated\n" + "    <T> void parametrizedAnnotated(T data);\n" + "}")
  }

  @Test
  fun testIDEA_54406() {
    formatter.doTextTest("public class Test1 {\n" +
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

  @Test
  fun testCommaInMethodDeclParamsList() {
    formatter.settings.SPACE_AFTER_COMMA = true
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
    formatter.doTextTest(before, after)
    formatter.settings.SPACE_AFTER_COMMA = false
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
    formatter.doTextTest(before, after)
  }


  @Test
  fun testFormatterOnOffTags() {
    formatter.settings.rootSettings.FORMATTER_TAGS_ENABLED = true
    formatter.settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    formatter.doTest()
  }

  @Test
  fun testFormatterOnOffTagsWithRegexp() {
    val settings = formatter.settings.rootSettings
    settings.FORMATTER_TAGS_ENABLED = true
    settings.FORMATTER_TAGS_ACCEPT_REGEXP = true
    settings.FORMATTER_OFF_TAG = "not.*format"
    settings.FORMATTER_ON_TAG = "end.*fragment"
    formatter.doTest()
  }

  @Test
  fun testDoNotIndentNotSelectedStatement_AfterSelectedOne() {
    formatter.textRange = TextRange(0, 73)
    formatter.doTextTest(
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

    formatter.textRange = TextRange(0, 67)
    formatter.doTextTest(
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

  @Test
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
    formatter.doTestWithDetectableIndentOptions(
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
    formatter.textRange = TextRange(1, original.length)
    formatter.doTestWithDetectableIndentOptions(
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

  @Test
  fun testKeepIndentsOnEmptyLines() {
    val indentOptions = formatter.settings.indentOptions
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
    formatter.doTextTest(
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
    formatter.doTextTest(
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

  @Test
  fun testIdea114862() {
    formatter.settings.rootSettings.FORMATTER_TAGS_ENABLED = true
    val indentOptions = formatter.settings.indentOptions
    assertNotNull(indentOptions)
    indentOptions!!.USE_TAB_CHARACTER = true
    formatter.doTextTest(
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

  @Test
  fun testIdea184461() {
    formatter.settings.rootSettings.FORMATTER_TAGS_ENABLED = true
    val indentOptions = formatter.settings.indentOptions
    assertNotNull(indentOptions)
    indentOptions!!.USE_TAB_CHARACTER = true
    formatter.doTextTest(
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

  @Test
  fun testReformatCodeWithErrorElementsWithoutAssertions() {
    formatter.doTextTest("class  RedTest   {   \n\n\n\n\n\n\n\n   " +
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

  @Test
  fun testReformatPackageAnnotation() {
    formatter.doTextTest(
      "@ParametersAreNonnullByDefault package com.example;",
      "@ParametersAreNonnullByDefault\n" + "package com.example;"
    )

    formatter.doTextTest(
      "        @ParametersAreNonnullByDefault\n" + "package com.example;",
      "@ParametersAreNonnullByDefault\n" + "package com.example;"
    )
  }

  @Test
  fun testFinal_OnTheEndOfLine() {
    formatter.doMethodTest(
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

  @Test
  fun testKeepTypeAnnotationNearType() {
    formatter.doTextTest(
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

  @Test
  fun testKeepSimpleSwitchInOneLine() {
    formatter.settings.CASE_STATEMENT_ON_NEW_LINE = false
    formatter.doMethodTest(
      "switch (b) {\n" +
      "case 1: case 2: break;\n" +
      "}",
      "switch (b) {\n" +
      "    case 1: case 2: break;\n" +
      "}")
  }

  @Test
  fun testExpandSwitch() {
    formatter.settings.CASE_STATEMENT_ON_NEW_LINE = false
    formatter.doMethodTest(
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

  @Test
  fun testKeepBreakOnSameLine() {
    formatter.settings.CASE_STATEMENT_ON_NEW_LINE = false
    formatter.doMethodTest(
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

  @Test
  fun testAnnotationAndFinalInsideParamList() {
    formatter.doClassTest(
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

  @Test
  fun testPreserveRbraceOnItsLine() {
    formatter.doClassTest(
      "class SomeTest {\n" +
      "  @Test\n" +
      "}",
      "class SomeTest {\n" +
      "    @Test\n" +
      "}"
    )
  }

  @Test
  fun testFormatCStyleCommentWithAsterisks() {
    formatter.doMethodTest(
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

  @Test
  fun testIdea183193() {
    formatter.doTextTest(
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
  @Test
  fun testParenthesesIndentation() {
    formatter.doTextTest(
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

  @Test
  fun testBlankLinesBeforeClassEnd () {
    formatter.settings.BLANK_LINES_BEFORE_CLASS_END = 2
    formatter.doTextTest(
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

  @Test
  fun testBlankLinesBeforeClassEnd_afterField () {
    formatter.settings.BLANK_LINES_BEFORE_CLASS_END = 2
    formatter.doTextTest(
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


  @Test
  fun testBlankLinesBeforeClassEnd_afterInnerClass () {
    formatter.settings.BLANK_LINES_BEFORE_CLASS_END = 2
    formatter.doTextTest(
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

  @Test
  fun testSpacing() {
    formatter.doTextTest(
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


  @Test
  fun testIdea192024() {
    formatter.settings.apply{
      RIGHT_MARGIN = 30
    }
    formatter.doTextTest(
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

  @Test
  fun testIdeaIDEA203464() {
    formatter.settings.apply{
      ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
      KEEP_LINE_BREAKS = false
    }
    formatter.doTextTest(
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


  @Test
  fun testIdeaIDEA198408() {
    formatter.settings.apply{
      ENUM_CONSTANTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
      KEEP_LINE_BREAKS = false
    }
    formatter.doTextTest(
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


  @Test
  fun testIdea195707() {
    formatter.settings.apply {
      CASE_STATEMENT_ON_NEW_LINE = true
      KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true
    }

    formatter.doTextTest(
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

  @Test
  fun testIdea195707_1() {
    formatter.settings.apply {
      CASE_STATEMENT_ON_NEW_LINE = false
      KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = true
    }

    formatter.doTextTest(
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

  @Test
  fun testFormatterTagsDisabled() {
    formatter.settings.rootSettings.apply {
      FORMATTER_TAGS_ENABLED = false
    }
    formatter.doTextTest(
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

  @Test
  fun testRecordHeaderLparenOnNewLine() {
    formatter.javaSettings.apply {
      NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = true
    }
    formatter.doTextTest("""
      record A(String s
        ) {}
    """.trimIndent(), """
      record A(
              String s
      ) {
      }
    """.trimIndent())
  }

  @Test
  fun testRecordHeaderLparenNotOnNewLine() {
    formatter.javaSettings.apply {
      NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = false
    }
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.doTextTest("""
      record A(String s
        ) {}
    """.trimIndent(), """
      record A(String s) {
      }
    """.trimIndent())
  }

  @Test
  fun testRecordHeaderRparenOnNewLine() {
    formatter.javaSettings.apply {
      RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = true
    }
    formatter.doTextTest("""
      record A(String s,
            String a) {}
    """.trimIndent(), """
      record A(String s,
               String a
      ) {
      }
    """.trimIndent())
  }

  @Test
  fun testRecordHeaderRparenNotOnNewLine() {
    formatter.javaSettings.apply {
      RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = false
    }
    formatter.settings.KEEP_LINE_BREAKS = false
    formatter.doTextTest("""
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

  @Test
  fun testRecordHeaderMultilineAlign() {
    formatter.javaSettings.apply {
      NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = false
      ALIGN_MULTILINE_RECORDS = true
    }
    formatter.doTextTest("""
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

  @Test
  fun testRecordHeaderNotMultilineAlign() {
    formatter.javaSettings.apply {
      NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = false
      ALIGN_MULTILINE_RECORDS = false
    }
    formatter.doTextTest("""
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

  @Test
  fun testInstanceofPatternWithGeneric() {

    formatter.doTextTest("""
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

  @Test
  fun testIdea154076() {
    formatter.doTextTest("""
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

  @Test
  fun testPermitsList() {
    formatter.settings.ALIGN_MULTILINE_EXTENDS_LIST = true
    formatter.doTextTest("sealed class A permits B, \n" + "C {}", "sealed class A permits B,\n" + "                       C {\n}")
  }

  @Test
  fun testWrapPermitsList() {
    formatter.settings.RIGHT_MARGIN = 50
    formatter.settings.EXTENDS_LIST_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    formatter.settings.EXTENDS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED

    formatter.doTextTest("sealed class ColtreDataProvider permits Provider, AgentEventListener, ParameterDataEventListener {\n}",
               "sealed class ColtreDataProvider permits Provider,\n" +
               "        AgentEventListener,\n" +
               "        ParameterDataEventListener {\n}")
  }


  @Test
  fun testPermitsListWithPrecedingGeneric() {
    formatter.doTextTest("""
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

  @Test
  fun testIdea250114() {
    formatter.doTextTest("""
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

  @Test
  fun testIdea252167() {
    formatter.doTextTest("""      
      @Ann record R(String str) {
      }
      """.trimIndent(),

      """
      @Ann
      record R(String str) {
      }
      """.trimIndent())
  }

  @Test
  fun testIdea153525() {
    formatter.settings.LAMBDA_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE_SHIFTED
    formatter.doTextTest(
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

  @Test
  fun testFormattingImplicitClassMembers() {
    formatter.doTextTest(
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

  @Test
  fun testFormattingImplicitClassWithCommentAfterImports() {
    formatter.doTextTest(
      """
      import jdk.incubator.vector.ByteVector;
      import jdk.incubator.vector.VectorSpecies;

      // this comment is messing up the code formatting
      VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

          void main() {
          }
      """.trimIndent(),

      """
      import jdk.incubator.vector.ByteVector;
      import jdk.incubator.vector.VectorSpecies;

      // this comment is messing up the code formatting
      VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

      void main() {
      }
      """.trimIndent()
    )
  }

  @Test
  fun testFormattingImplicitClassWithCommentBetweenMethods() {
    formatter.doTextTest(
      """
      import static java.lang.System.out;

      void main() {
          out.println("Hello");
      }

      // Comment between methods
              void test() {
                  test();
              }
      """.trimIndent(),

      """
      import static java.lang.System.out;

      void main() {
          out.println("Hello");
      }

      // Comment between methods
      void test() {
          test();
      }
      """.trimIndent()
    )
  }

  @Test
  fun testDoNotFormatLongChains() {
    formatter.settings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.doTest()
  }
}