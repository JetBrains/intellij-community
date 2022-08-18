// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AbstractEnterActionTestCase;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaEnterActionTest extends AbstractEnterActionTestCase {
  private CommonCodeStyleSettings getJavaCommonSettings() {
    return CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testAfterLbrace1() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "Before1.java");
    performAction();
    checkResultByFile(path + "After1.java");
  }

  public void testAfterLbrace2() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "Before2.java");
    performAction();
    checkResultByFile(path + "After2.java");
  }

  public void testLineCommentSCR13247() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "SCR13247.java");
    performAction();
    checkResultByFile(path + "SCR13247_after.java");
  }

  public void testLineCommentAtLineEnd() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "AtLineEnd.java");
    performAction();
    checkResultByFile(path + "AtLineEnd_after.java");
  }

  public void testLineCommentBeforeAnother() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "BeforeAnother.java");
    performAction();
    checkResultByFile(path + "BeforeAnother_after.java");
  }

  public void testLineCommentBeforeAnotherWithSpace() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "BeforeAnotherWithSpace.java");
    performAction();
    checkResultByFile(path + "BeforeAnotherWithSpace_after.java");
  }

  public void testLineCommentAtTrailingSpaces() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "AtTrailingSpaces.java");
    performAction();
    checkResultByFile(path + "AtTrailingSpaces_after.java");
  }

  public void testAfterLbrace3() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "Before3.java");
    performAction();
    checkResultByFile(path + "After3.java");
  }

  public void testWithinBraces() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "WithinBraces.java");
    performAction();
    checkResultByFile(path + "WithinBraces_after.java");
  }

  public void testWithinBracesWithSpaceBeforeCaret() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "WithinBracesWithSpaceBeforeCaret.java");
    performAction();
    checkResultByFile(path + "WithinBracesWithSpaceBeforeCaret_after.java");
  }

  public void testWithinBracesWithSpaceAfterCaret() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "WithinBracesWithSpaceAfterCaret.java");
    performAction();
    checkResultByFile(path + "WithinBracesWithSpaceAfterCaret_after.java");
  }

  public void testAfterLbraceWithStatement1() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "LbraceWithStatement1.java");
    performAction();
    checkResultByFile(path + "LbraceWithStatement1_after.java");
  }

  public void testAfterLbraceWithSemicolon() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "LbraceWithSemicolon.java");
    performAction();
    checkResultByFile(path + "LbraceWithSemicolon_after.java");
  }

  public void testAfterLbraceWithRParen() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "LbraceWithRParen.java");
    performAction();
    checkResultByFile(path + "LbraceWithRParen_after.java");
  }


  public void testJavaDoc1() {
    String path = "/codeInsight/enterAction/javaDoc/";

    configureByFile(path + "state0.java");
    performAction();
    checkResultByFile(path + "state1.java");
    performAction();
    checkResultByFile(path + "state2.java");
  }

  public void testJavaDoc3() {
    configureByFile("/codeInsight/enterAction/javaDoc/before1.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/after1.java");
  }

  public void testJavaDoc4() {
    configureByFile("/codeInsight/enterAction/javaDoc/before2.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/after2.java");
  }

  public void testJavaDoc5() {
    configureByFile("/codeInsight/enterAction/javaDoc/before3.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/after3.java");
  }

  public void testJavaDoc6() {
    configureByFile("/codeInsight/enterAction/javaDoc/before4.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/after4.java");
  }

  public void testJavaDoc7() {
    configureByFile("/codeInsight/enterAction/javaDoc/before5.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/after5.java");
  }

  public void testJavaDoc8() {
    configureByFile("/codeInsight/enterAction/javaDoc/before6.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/after6.java");
  }

  public void testJavaDoc9() {
    // IDEA-64896
    configureByFile("/codeInsight/enterAction/javaDoc/beforeCommentEndLike.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/afterCommentEndLike.java");
  }

  public void testJavaDocInlineTag() {
    configureByFile("/codeInsight/enterAction/javaDoc/beforeInlineTag.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/afterInlineTag.java");
  }

  public void testBug1() {
    configureByFile("/codeInsight/enterAction/bug1/Before.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/bug1/After.java");
  }

  public void testSplitLiteral() {
    configureByFile("/codeInsight/enterAction/splitLiteral/Before.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/splitLiteral/After.java");
  }

  public void testSplitLiteralInEscape() {
    configureByFile("/codeInsight/enterAction/splitLiteral/Escape.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/splitLiteral/Escape_after.java");
  }

  public void testIndentInArgList1() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    configureByFile("/codeInsight/enterAction/indent/InArgList1.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/indent/InArgList1_after.java");
  }

  public void testIndentInArgList2() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    configureByFile("/codeInsight/enterAction/indent/InArgList2.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/indent/InArgList2_after.java");
  }

  public void testIndentInArgList3() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    configureByFile("/codeInsight/enterAction/indent/InArgList3.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/indent/InArgList3_after.java");
  }

  public void testIndentInArgList4() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    configureByFile("/codeInsight/enterAction/indent/InArgList4.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/indent/InArgList4_after.java");
  }


  public void testNoJavadocStub() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean old = settings.JAVADOC_STUB_ON_ENTER;
    settings.JAVADOC_STUB_ON_ENTER = false;

    try {
      configureByFile("/codeInsight/enterAction/settings/NoJavadocStub.java");
      performAction();
      checkResultByFile("/codeInsight/enterAction/settings/NoJavadocStub_after.java");
    }
    finally {
      settings.JAVADOC_STUB_ON_ENTER = old;
    }
  }

  public void testNoCloseComment() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean old = settings.CLOSE_COMMENT_ON_ENTER;
    settings.CLOSE_COMMENT_ON_ENTER = false;

    try {
      doTextTest("java",
                 "/*<caret>",
                 "/*\n");
    }
    finally {
      settings.CLOSE_COMMENT_ON_ENTER = old;
    }
  }

  public void testNoCloseJavaDocComment() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean old = settings.CLOSE_COMMENT_ON_ENTER;
    settings.CLOSE_COMMENT_ON_ENTER = false;

    try {
      doTextTest("java",
                 "/**<caret>",
                 "/**\n <caret>");
    }
    finally {
      settings.CLOSE_COMMENT_ON_ENTER = old;
    }
  }
  public void testNoInsertBrace() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean old = settings.INSERT_BRACE_ON_ENTER;
    settings.INSERT_BRACE_ON_ENTER = false;

    try {
      configureByFile("/codeInsight/enterAction/settings/NoInsertBrace.java");
      performAction();
      checkResultByFile("/codeInsight/enterAction/settings/NoInsertBrace_after.java");
    }
    finally {
      settings.INSERT_BRACE_ON_ENTER = old;
    }
  }

  public void testNoSmartIndent() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean old = settings.SMART_INDENT_ON_ENTER;
    settings.SMART_INDENT_ON_ENTER = false;

    try {
      configureByFile("/codeInsight/enterAction/settings/NoSmartIndent.java");
      performAction();
      checkResultByFile("/codeInsight/enterAction/settings/NoSmartIndent_after.java");
    }
    finally {
      settings.SMART_INDENT_ON_ENTER = old;
    }
  }

  public void testNoSmartIndentInJavadoc() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean indent = settings.SMART_INDENT_ON_ENTER;
    settings.SMART_INDENT_ON_ENTER = false;
    boolean stub = settings.JAVADOC_STUB_ON_ENTER;
    settings.JAVADOC_STUB_ON_ENTER = false;

    try {
      configureByFile("/codeInsight/enterAction/settings/NoJavadocStub.java");
      performAction();
      checkResultByFile(null, "/codeInsight/enterAction/settings/NoJavadocStub_after.java", true); // side effect...
    }
    finally {
      settings.SMART_INDENT_ON_ENTER = indent;
      settings.JAVADOC_STUB_ON_ENTER = stub;
    }
  }

  public void testNoSmartIndentInsertBrace() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean old = settings.SMART_INDENT_ON_ENTER;
    settings.SMART_INDENT_ON_ENTER = false;

    try {
      configureByFile("/codeInsight/enterAction/settings/NoSmartIndentInsertBrace.java");
      performAction();
      checkResultByFile("/codeInsight/enterAction/settings/NoSmartIndentInsertBrace_after.java");
    }
    finally {
      settings.SMART_INDENT_ON_ENTER = old;
    }
  }

  public void testSCR26493() {
    configureByFile("/codeInsight/enterAction/SCR26493/before1.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/SCR26493/after1.java");
  }

  public void testBeforeElse() {
    configureByFile("/codeInsight/enterAction/BeforeElse_before.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/BeforeElse_after.java");
  }


  public void testBinaryExpressionAsParameter() throws Exception {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
  }

  public void testSimpleEnter() throws Exception {
    doTest();
  }

  public void testSimpleEnter2() throws Exception {
    doTest();
  }

  public void testSCR1647() {
    CodeStyleSettings settings = getCodeStyleSettings();
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    options.INDENT_SIZE = 4;
    options.TAB_SIZE = 2;
    configureByFile("/codeInsight/enterAction/SCR1647.java");
    performAction();
    checkResultByFile(null, "/codeInsight/enterAction/SCR1647_after.java", false);
  }

  public void testPerformance() {
    configureByFile("/codeInsight/enterAction/Performance.java");
    PlatformTestUtil.startPerformanceTest("enter in " + getFile(), 100, () -> {
      performAction();
      deleteLine();
      caretUp();
    }).assertTiming();
  }

  public void testComment() throws Exception {
    doTest();
  }

  public void testSaveSpaceAfter() throws Exception {
    doTest();
  }

  public void testEndOfClass() throws Exception {
    doTest();
  }

  public void testInsideClassFile() throws Exception {
    doTest();
  }

  public void testGetLineIndent() {
    CodeStyleSettings settings = getCodeStyleSettings();
    final CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(JavaFileType.INSTANCE);
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.SMART_TABS = true;
    indentOptions.TAB_SIZE = 2;
    indentOptions.INDENT_SIZE = 2;
    indentOptions.CONTINUATION_INDENT_SIZE = 8;
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    String text = "class Foo {\n" +
                  "\tint\n" +
                  "\n" +
                  "\t\t\t\t\tmyField;\n" +
                  "\n" +
                  "\tvoid foo (int i,\n" +
                  "\n" +
                  "\t          int j\n" +
                  "\n) {\n" +
                  "\t}" +
                  "}";
    PsiFileFactory factory = PsiFileFactory.getInstance(PsiManager.getInstance(getProject()).getProject());
    final PsiFile file = factory.createFileFromText("a.java", JavaFileType.INSTANCE, text, LocalTimeCounter.currentTime(), true);
    doGetIndentTest(file, 2, "\t\t\t\t\t");
    doGetIndentTest(file, 4, "\t");
    doGetIndentTest(file, 6, "\t          ");
  }

  public void _testSCR1488() {
    JavaCodeStyleSettings.getInstance(getProject()).JD_LEADING_ASTERISKS_ARE_ENABLED = false;
    doTextTest("java", "class Foo {\n" + "/**<caret>\n" + "    public int foo(int i, int j, int k) throws IOException {\n" + "    }" + "}",
               "class Foo {\n" + "    /**\n" + "     <caret>\n" + "     @param i\n" + "     @param j\n" + "     @param k\n" +
               "     @return\n" + "     @throws IOException\n" + "     */\n" +
               "    public int foo(int i, int j, int k) throws IOException {\n" + "    }}");
  }

  public void testJavaDocSplitWhenLeadingAsterisksAreDisabled() throws Exception {
    JavaCodeStyleSettings.getInstance(getProject()).JD_LEADING_ASTERISKS_ARE_ENABLED = false;
    doTest();
  }

  public void testSCR1535() {
    doTextTest("java", "/**\n" + "*/<caret>\n" + "class Foo{}", "/**\n" + "*/\n" + "<caret>\n" + "class Foo{}");

    doTextTest("java", "class Foo {\n" + "    /**\n" + "    */<caret>\n" + "    void foo() {}\n" + "}",
               "class Foo {\n" + "    /**\n" + "    */\n" + "    <caret>\n" + "    void foo() {}\n" + "}");

    doTextTest("java", "class Foo {\n" + "    /**\n" + "    */<caret>\n" + "    abstract void foo();\n" + "}",
               "class Foo {\n" + "    /**\n" + "    */\n" + "    <caret>\n" + "    abstract void foo();\n" + "}");

    doTextTest("java", "class Foo {\n" + "    /**\n" + "    */<caret>\n" + "    int myFoo;\n" + "}",
               "class Foo {\n" + "    /**\n" + "    */\n" + "    <caret>\n" + "    int myFoo;\n" + "}");


  }

  public void testSCR3006() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).INDENT_CASE_FROM_SWITCH = false;
    doTextTest("java", "class Foo {\n" + "    void foo(){\n" + "        switch (foo) { \n" + "        case 1: \n" +
                       "            doSomething();<caret> \n" + "            break; \n" + "  } " + "    }\n" + "}", "class Foo {\n" +
                                                                                                                    "    void foo(){\n" +
                                                                                                                    "        switch (foo) { \n" +
                                                                                                                    "        case 1: \n" +
                                                                                                                    "            doSomething();\n" +
                                                                                                                    "            <caret>\n" +
                                                                                                                    "            break; \n" +
                                                                                                                    "  } " + "    }\n" +
                                                                                                                    "}");
  }
  public void testSCRblabla() {

    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).INDENT_CASE_FROM_SWITCH = true;

    doTextTest("java", "class Foo {\n" + "    void foo(){\n" + "        switch (foo) { \n" + "            case 1: \n" +
                       "                doSomething();<caret> \n" + "                break; \n" + "  } " + "    }\n" + "}",
               "class Foo {\n" + "    void foo(){\n" + "        switch (foo) { \n" + "            case 1: \n" +
               "                doSomething();\n" + "                <caret>\n" + "                break; \n" + "  } " + "    }\n" +
               "}");

  }


  public void testSCR1692() {
    doTextTest("java", "public class TryFinallyCatch {\n" + "    public static void main(String[] args) {\n" + "        try {\n" +
                       "            System.out.println(\"Hello\");\n" + "        }<caret>\n" + "        finally{ \n" + "        }\n" +
                       "    }\n" + "}", "public class TryFinallyCatch {\n" + "    public static void main(String[] args) {\n" +
                                        "        try {\n" + "            System.out.println(\"Hello\");\n" + "        }\n" +
                                        "        <caret>\n" + "        finally{ \n" + "        }\n" + "    }\n" + "}");
  }

  public void testSCR1696() {
    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        for(;;)<caret>\n" + "        foo();" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        for(;;)\n" + "            <caret>\n" + "        foo();" + "    }\n" + "}");

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        for(;;) {<caret>\n" + "        foo();}" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        for(;;) {\n" + "            <caret>\n" + "        foo();}" + "    }\n" +
               "}");

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        for(;;) <caret>{\n" + "        foo();}" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        for(;;) \n" + "        <caret>{\n" + "        foo();}" + "    }\n" + "}");

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        for(;<caret>;) {\n" + "        foo();}" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        for(;\n" + "                <caret>;) {\n" + "        foo();}" +
               "    }\n" + "}");

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        for(;;)<caret>\n" + "    }\n" + "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        for(;;)\n" +
               "            <caret>\n" +
               "    }\n" + "}");

  }

  public void testSCR1698() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).KEEP_FIRST_COLUMN_COMMENT = false;
    doTextTest("java",
               "class A {\n" + "      void foo() {<caret>\n" + "/*\n" + "    previousContent();\n" + "*/\n" + "       }\n" + "\n" + "}",
               "class A {\n" + "      void foo() {\n" + "          <caret>\n" + "/*\n" + "    previousContent();\n" + "*/\n" +
               "       }\n" + "\n" + "}");
  }

  public void testInsideParameterList() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        foo(1,<caret>);\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        foo(1,\n" + "                <caret>);\n" + "    }\n" + "}");

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        foo(1,<caret>\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        foo(1,\n" + "                <caret>\n" + "    }\n" + "}");
  }
  public void testBlaBla() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        foo(1,<caret>\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        foo(1,\n" + "            <caret>\n" + "    }\n" + "}");

  }

  public void testInsideFor() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_FOR = true;
    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        for(int i = 0;<caret>\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        for(int i = 0;\n" + "            <caret>\n" + "    }\n" + "}");

    doTextTest("java",
               "class Foo {\n" + "    void foo() {\n" + "        for(int i = 0;\n" + "            i < 10;<caret>\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        for(int i = 0;\n" + "            i < 10;\n" + "            <caret>\n" +
               "    }\n" + "}");
  }

  public void testEnterInImplementsList() {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_EXTENDS_LIST = false;
    doTextTest("java", "class A implements B,<caret>\n" + "{\n" + "}", "class A implements B,\n" + "        <caret>\n" + "{\n" + "}");
  }

  public void testEnterBlaBla() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;
    doTextTest("java", "class A implements B,<caret>\n" + "{\n" + "}",
               "class A implements B,\n" + "                   <caret>\n" + "{\n" + "}");

  }

  public void testInsideCodeBlock() {
    doTextTest("java", "class Foo{\n" + "    void foo() {\n" + "        int[] i = new int[] {1,2,3,4,5<caret>\n" + "        ,6,7,8}\n" +
                       "    }\n" + "}", "class Foo{\n" + "    void foo() {\n" + "        int[] i = new int[] {1,2,3,4,5\n" +
                                        "                <caret>\n" + "        ,6,7,8}\n" + "    }\n" + "}");
  }

  public void testInsideAnonymousClass() {
    doTextTest("java", "class Foo{\n" + "    void foo() {\n" + "        Runnable i = new Runnable() {\n" +
                       "            public void foo1(){}\n" + "            public void foo2(){}\n" + "            public void foo3(){}\n" +
                       "            public void foo4(){}\n" + "            <caret>\n" + "        }\n" + "    }\n" + "}", "class Foo{\n" +
                                                                                                                         "    void foo() {\n" +
                                                                                                                         "        Runnable i = new Runnable() {\n" +
                                                                                                                         "            public void foo1(){}\n" +
                                                                                                                         "            public void foo2(){}\n" +
                                                                                                                         "            public void foo3(){}\n" +
                                                                                                                         "            public void foo4(){}\n" +
                                                                                                                         "            \n" +
                                                                                                                         "            <caret>\n" +
                                                                                                                         "        }\n" +
                                                                                                                         "    }\n" + "}");
  }

  public void testEnterInConditionOperation() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTextTest("java", "class Foo {\n" + "    void foo () {\n" + "        int var = condition ?<caret>\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo () {\n" + "        int var = condition ?\n" + "                  <caret>\n" + "    }\n" +
               "}");
  }

  public void testInsideIfCondition() throws Exception {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTextTest("java", "class Foo{\n" + "    void foo() {\n" + "        if(A != null &&\n" + "           B != null &&<caret>\n" +
                       "          ) {\n" + "        }\n" + "    }\n" + "}", "class Foo{\n" + "    void foo() {\n" +
                                                                            "        if(A != null &&\n" + "           B != null &&\n" +
                                                                            "           <caret>\n" + "          ) {\n" + "        }\n" +
                                                                            "    }\n" + "}");
  }

  public void testInsideIfCondition_2() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        if (info.myTargetElement != null &&\n" +
                       "          info.myElementAtPointer != null && <caret>info.myTargetElement != info.myElementAtPointer) {\n" +
                       "        }\n" + "    }\n" + "}", "class Foo {\n" + "    void foo() {\n" +
                                                        "        if (info.myTargetElement != null &&\n" +
                                                        "          info.myElementAtPointer != null && \n" +
                                                        "            <caret>info.myTargetElement != info.myElementAtPointer) {\n" +
                                                        "        }\n" + "    }\n" + "}");
  }

  public void testBreakingElseIfWithoutBraces() {
    // Inspired by IDEA-60304.
    doTextTest(
      "java",
      "class Foo {\n" +
      "    void test() {\n" +
      "        if (foo()) {\n" +
      "        } else {<caret> if (bar())\n" +
      "            quux();\n" +
      "    }\n" +
      "}",
      "class Foo {\n" +
      "    void test() {\n" +
      "        if (foo()) {\n" +
      "        } else {\n" +
      "            if (bar())\n" +
      "                quux();\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testBreakingElseIfWithBraces() {
    // Inspired by IDEA-60304.
    doTextTest(
      "java",
      "class Foo {\n" +
      "    void test() {\n" +
      "        if (foo()) {\n" +
      "        } else {<caret> if (bar()) {\n" +
      "            quux();\n" +
      "        }\n" +
      "    }\n" +
      "}",
      "class Foo {\n" +
      "    void test() {\n" +
      "        if (foo()) {\n" +
      "        } else {\n" +
      "            if (bar()) {\n" +
      "                quux();\n" +
      "            }\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testSCR2238() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        switch (a) {<caret>\n" + "        }\n" + "    }\n" + "}",
               "class Foo {\n" + "    void foo() {\n" + "        switch (a) {\n" + "            <caret>\n" + "        }\n" + "    }\n" +
               "}");

    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        switch (a) {\n" + "            case 1:\n" + "            {\n" +
                       "            }<caret>\n" + "        }\n" + "    }\n" + "}", "class Foo {\n" + "    void foo() {\n" +
                                                                                   "        switch (a) {\n" + "            case 1:\n" +
                                                                                   "            {\n" + "            }\n" +
                                                                                   "            <caret>\n" + "        }\n" + "    }\n" +
                                                                                   "}");


    doTextTest("java",

               "class Foo {\n" +
               "    void foo() {\n" +
               "        switch (a) {\n" +
               "            case 1:\n" +
               "                foo();<caret>\n" +
               "        }\n" +
               "    }\n" +
               "}",

               "class Foo {\n" +
               "    void foo() {\n" +
               "        switch (a) {\n" +
               "            case 1:\n" +
               "                foo();\n" +
               "                <caret>\n" +
               "        }\n" +
               "    }\n" + "}");


    doTextTest("java",

               "class Foo {\n" +
               "    void foo() {\n" +
               "        switch (a) {\n" +
               "            case 1:\n" +
               "                foo();\n" +
               "                break;<caret>\n" +
               "        }\n" +
               "    }\n" +
               "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        switch (a) {\n" +
               "            case 1:\n" +
               "                foo();\n" +
               "                break;\n" +
               "                <caret>\n" +
               "        }\n" +
               "    }\n" + "}");

    doTextTest("java",

               "class Foo {\n" +
               "    void foo() {\n" +
               "        switch (a) {\n" +
               "            case 1:\n" +
               "                foo();\n" +
               "                return;<caret>\n" +
               "        }\n" +
               "    }\n" +
               "}",

               "class Foo {\n" +
               "    void foo() {\n" +
               "        switch (a) {\n" +
               "            case 1:\n" +
               "                foo();\n" +
               "                return;\n" +
               "                <caret>\n" +
               "        }\n" +
               "    }\n" +
               "}");


  }


  public void testEnterAfterSecondAnnotation() {
    doTextTest("java", "@A\n" + "@B<caret>\n" + "class C{}", "@A\n" + "@B\n" + "<caret>\n" + "class C{}");
  }

  public void testIncompleteBeginOfFile() {
    doTextTest("java",
               "public class Test {\n" +
               "" + "  public void foo(){\n" +
               "" + "    if (a)<caret>",
               "public class Test {\n" +
               "  public void foo(){\n" +
               "    if (a)\n" +
               "        <caret>");
  }

  public void testAtEndOfFile() {
    doTextTest("java",
               "public class Test {\n" +
               "  public void foo(){\n" +
               "  }\n" +
               "       <caret>",
               "public class Test {\n" +
               "  public void foo(){\n" +
               "  }\n" +
               "       \n" +
               "  <caret>"
    );
  }

  public void testStringLiteralAsReferenceExpression() {
    doTextTest("java",
               "public class Test {\n" +
               "  {\n" +
               "    String q = \"abcdef<caret>ghijkl\".replaceAll(\"KEY\", \"key\");\n" +
               "  }\n" +
               "}",

               "public class Test {\n" +
               "  {\n" +
               "    String q = (\"abcdef\" +\n" +
               "            \"<caret>ghijkl\").replaceAll(\"KEY\", \"key\");\n" +
               "  }\n" +
               "}"
    );
  }

  public void testLineCommentInJavadoc() {
    doTextTest("java",
               "  abstract class Test {\n" +
               "    /**<caret>Foo//bar */\n" +
               "    public abstract void foo();\n" +
               "  }",

               "  abstract class Test {\n" +
               "    /**\n" +
               "     * <caret>Foo//bar */\n" +
               "    public abstract void foo();\n" +
               "  }"
    );
  }

  public void testLineCommentInBlock() {
    doTextTest("java",
               "  abstract class Test {\n" +
               "    /*\n" +
               "     * <caret>Foo//bar */\n" +
               "    public abstract void foo();\n" +
               "  }",

               "  abstract class Test {\n" +
               "    /*\n" +
               "     * \n" +
               "     * <caret>Foo//bar */\n" +
               "    public abstract void foo();\n" +
               "  }"
    );
  }

  public void testIDEADEV_28200() {
    doTextTest("java",
               "class Foo {\n" +
               "    public void context() {\n" +
               "\t\tint v = 0;<caret>\n" +
               "\t\tmyField += v;\n" +
               "\t}" +
               "}",
               "class Foo {\n" +
               "    public void context() {\n" +
               "\t\tint v = 0;\n" +
               "        <caret>\n" +
               "\t\tmyField += v;\n" +
               "\t}" +
               "}");
  }

  public void testIndentStatementAfterIf() {
    doTextTest("java",
               "class Foo {\n" +
               "    void foo () {\n" +
               "        if(blah==3)<caret>\n" +
               "    }\n" +
               "}",
               "class Foo {\n" +
               "    void foo () {\n" +
               "        if(blah==3)\n" +
               "            <caret>\n" +
               "    }\n" +
               "}"
    );
  }

  public void testIDEADEV_14102() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTextTest("java",
               "class Foo {\n" +
               "    void foo () {\n" +
               "        PsiSubstitutor s = aClass == null ?\n" +
               "        PsiSubstitutor.EMPTY : <caret>\n" +
               "    }\n" +
               "}",
               "class Foo {\n" +
               "    void foo () {\n" +
               "        PsiSubstitutor s = aClass == null ?\n" +
               "        PsiSubstitutor.EMPTY : \n" +
               "                           <caret>\n" + // Aligned with 'aClass'!
               "    }\n" +
               "}"
    );
  }

  public void testAfterTryBlock() {
    doTextTest("java",
               "class Foo {\n" +
               "    void foo () {\n" +
               "        try {}<caret>\n" +
               "    }\n" +
               "}",
               "class Foo {\n" +
               "    void foo () {\n" +
               "        try {}\n" +
               "        <caret>\n" +
               "    }\n" +
               "}"
    );

  }

  public void testTryCatch() {
    doTextTest("java",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        try {\n" +
               "        \n" +
               "        } catch () {<caret>\n" +
               "    }\n" +
               "}",
               "class Foo {\n" +
               "    void foo() {\n" +
               "        try {\n" +
               "        \n" +
               "        } catch () {\n" +
               "            <caret>\n" +
               "        }\n" +
               "    }\n" +
               "}");
  }

  public void testEnterBetweenBracesAtJavadoc() {
    // Inspired by IDEA-61221
    doTextTest(
      "java",
      "/**\n" +
      " *    class Foo {<caret>}\n" +
      " */" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " *    class Foo {\n" +
      " *        <caret>\n" +
      " *    }\n" +
      " */" +
      "class Test {\n" +
      "}"
    );
  }

  public void testEnterBetweenNestedJavadocTag() {
    doTextTest(
      "java",
      "/**\n" +
      " *    <outer><inner><caret></inner></outer>\n" +
      " */" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " *    <outer><inner>\n" +
      " *        <caret>\n" +
      " *    </inner></outer>\n" +
      " */" +
      "class Test {\n" +
      "}"
    );
  }

  public void testIndentAfterStartJavadocTag() {
    doTextTest(
      "java",
      "/**\n" +
      " *    <pre><caret>\n" +
      " */" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " *    <pre>\n" +
      " *        <caret>\n" +
      " */" +
      "class Test {\n" +
      "}"
    );
  }

  public void testEnterAfterEmptyJavadocTagIsNotIndented() {
    // Inspired by IDEA-65031
    doTextTest(
      "java",
      "/**\n" +
      " *    <p/><caret>\n" +
      " */" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " *    <p/>\n" +
      " *    <caret>\n" +
      " */" +
      "class Test {\n" +
      "}"
    );
  }

  public void testEnterBetweenJavadocTagsProducesNewLine() {
    doTextTest(
      "java",
      "/**\n" +
      " *    <pre><caret></pre>\n" +
      " */" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " *    <pre>\n" +
      " *        <caret>\n" +
      " *    </pre>\n" +
      " */" +
      "class Test {\n" +
      "}"
    );
  }

  public void testTextBetweenJavadocTagStartAndCaret() {
    doTextTest(
      "java",
      "/**\n" +
      " *    <pre>a<caret></pre>\n" +
      " */" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " *    <pre>a\n" +
      " *    <caret></pre>\n" +
      " */" +
      "class Test {\n" +
      "}"
    );
  }

  public void testTextBetweenCaretAndJavadocEndTag() {
    doTextTest(
      "java",
      "/**\n" +
      " *    <pre><caret>text</pre>\n" +
      " */" +
      "class Test {\n" +
      "}",
      "/**\n" +
      " *    <pre>\n" +
      " *        <caret>text\n" +
      " *    </pre>\n" +
      " */" +
      "class Test {\n" +
      "}"
    );
  }

  public void testInitBlockAtAnonymousInnerClass() {
    doTextTest(
      "java",
      "class Test {\n" +
      "    public void test() {\n" +
      "        new Foo(new Bar() {{<caret>);\n" +
      "    }\n" +
      "}",
      "class Test {\n" +
      "    public void test() {\n" +
      "        new Foo(new Bar() {{\n" +
      "            <caret>\n" +
      "        }});\n" +
      "    }\n" +
      "}"
    );
  }

  public void testAfterWrappedNonFinishedMethodCallExpression() {
    // Inspired by IDEA-64989
    doTextTest(
      "java",
      "class Test {\n" +
      "    public void test() {\n" +
      "        new Foo()\n" +
      "                .bar()<caret>\n" +
      "    }\n" +
      "}",
      "class Test {\n" +
      "    public void test() {\n" +
      "        new Foo()\n" +
      "                .bar()\n" +
      "                <caret>\n" +
      "    }\n" +
      "}"
    );
  }

  public void testDoNotGrabUnnecessaryEndDocCommentSymbols() {
    // Inspired by IDEA-64896
    doTextTest(
      "java",
      "/**<caret>\n" +
      "public class BrokenAlignment {\n" +
      "\n" +
      "    int foo() {\n" +
      "       return 1 */*comment*/ 1;\n" +
      "    }\n" +
      "}",
      "/**\n" +
      " * <caret>\n" +
      " */\n" +
      "public class BrokenAlignment {\n" +
      "\n" +
      "    int foo() {\n" +
      "       return 1 */*comment*/ 1;\n" +
      "    }\n" +
      "}"
    );
  }

  public void testAlignmentIndentAfterIncompleteImplementsBlock() {
    // Inspired by IDEA-65777
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;

    doTextTest(
      "java",
      "public abstract class BrokenAlignment\n" +
      "        implements Comparable,<caret> {\n" +
      "\n" +
      "}",
      "public abstract class BrokenAlignment\n" +
      "        implements Comparable,\n" +
      "                   <caret>{\n" +
      "\n" +
      "}"
    );
  }

  public void testFirstLineOfJavadocParameter() {
    // Inspired by IDEA-IDEA-70194
    doTextTest(
      "java",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * @param i  this is my description<caret>\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * @param i  this is my description\n" +
      "     *           <caret>\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}"
    );
  }

  public void testSecondLineOfJavadocParameter() {
    // Inspired by IDEA-IDEA-70194
    doTextTest(
      "java",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * @param i  this is my description\n" +
      "     *           that spreads multiple lines<caret>\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * @param i  this is my description\n" +
      "     *           that spreads multiple lines\n" +
      "     *           <caret>\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}"
    );
  }

  public void testInsideJavadocParameter() {
    // Inspired by IDEA-IDEA-70194
    doTextTest(
      "java",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * @param i  this is my <caret>description\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * @param i  this is my \n" +
      "     *           <caret>description\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}"
    );
  }

  public void testBeforeJavadocParameter() {
    // Inspired by IDEA-IDEA-70194
    doTextTest(
      "java",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * <caret>@param i  this is my description\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * \n" +
      "     * <caret>@param i  this is my description\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}"
    );
  }

  public void testInsideJavadocParameterWithCodeStyleToAvoidAlignment() {
    // Inspired by IDEA-75802
    JavaCodeStyleSettings.getInstance(getProject()).JD_ALIGN_PARAM_COMMENTS = false;
    doTextTest(
      "java",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * @param i  this is my <caret>description\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}",
      "abstract class Test {\n" +
      "    /**\n" +
      "     * @param i  this is my \n" +
      "     * <caret>description\n" +
      "     */\n" +
      "     void test(int i) {\n" +
      "     }\n" +
      "}"
    );
  }

  public void testCommentAtFileEnd() {
    doTextTest("java", "/*<caret>", "/*\n" +
                                    "<caret>\n" +
                                    " */");
  }

  public void testEnterAfterIfCondition() {
    String before = "public class Test {\n" +
                    "    public void main() {\n" +
                    "       if (true){<caret> return;\n" +
                    "       System.out.println(\"!\");\n" +
                    "    }\n" +
                    "}";
    String after = "public class Test {\n" +
                   "    public void main() {\n" +
                   "       if (true){\n" +
                   "           return;\n" +
                   "       }\n" +
                   "       System.out.println(\"!\");\n" +
                   "    }\n" +
                   "}";
    doTextTest("java", before, after);
  }

  public void testNoneIndentAfterMethodAnnotation() {
    String before = "class Test {\n" +
                    "    @Get<caret>\n" +
                    "    void test() {\n" +
                    "    }\n" +
                    "}";
    String after = "class Test {\n" +
                   "    @Get\n" +
                   "    <caret>\n" +
                   "    void test() {\n" +
                   "    }\n" +
                   "}";
    doTextTest("java", before, after);
  }

  public void testAfterDocComment() {
    doTextTest("java",
               "class Test {\n" +
               "    /*\n" +
               "     * */<caret>void m() {}\n" +
               "}",
               "class Test {\n" +
               "    /*\n" +
               "     * */\n" +
               "    <caret>void m() {}\n" +
               "}");
  }

  public void testEnumWithoutContinuation() {
    doTextTest("java",
               "public <caret>enum X {}",
               "public \n" +
               "enum X {}");
  }

  public void testEnterInArrayDeclaration() {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doTextTest("java",
               "public class CucumberInspectionsProvider implements InspectionToolProvider {\n" +
               "  public Class[] getInspectionClasses() {\n" +
               "    return new Class[] { CucumberStepInspection.class,\n" +
               "                         CucumberMissedExamplesInspection.class,\n" +
               "                         CucumberExamplesColonInspection.class, <caret>\n" +
               "    };\n" +
               "  }\n" +
               "}",
               "public class CucumberInspectionsProvider implements InspectionToolProvider {\n" +
               "  public Class[] getInspectionClasses() {\n" +
               "    return new Class[] { CucumberStepInspection.class,\n" +
               "                         CucumberMissedExamplesInspection.class,\n" +
               "                         CucumberExamplesColonInspection.class, \n" +
               "                         <caret>\n" +
               "    };\n" +
               "  }\n" +
               "}");

    doTextTest("java",
               "public class CucumberInspectionsProvider implements InspectionToolProvider {\n" +
               "  public Class[] getInspectionClasses() {\n" +
               "    return new Class[] { CucumberStepInspection.class,\n" +
               "                         CucumberMissedExamplesInspection.class,\n" +
               "                         CucumberExamplesColonInspection.class, <caret>};\n" +
               "  }\n" +
               "}",
               "public class CucumberInspectionsProvider implements InspectionToolProvider {\n" +
               "  public Class[] getInspectionClasses() {\n" +
               "    return new Class[] { CucumberStepInspection.class,\n" +
               "                         CucumberMissedExamplesInspection.class,\n" +
               "                         CucumberExamplesColonInspection.class, \n" +
               "                         <caret>};\n" +
               "  }\n" +
               "}");
  }

  public void testEnterInArrayDeclaration_BeforeRBrace() {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doTextTest("java",
               "public class CucumberInspectionsProvider implements InspectionToolProvider {\n" +
               "  public Class[] getInspectionClasses() {\n" +
               "    return new Class[] { CucumberStepInspection.class,\n" +
               "                         CucumberMissedExamplesInspection.class,\n" +
               "                         CucumberExamplesColonInspection.class<caret>};\n" +
               "  }\n" +
               "}",
               "public class CucumberInspectionsProvider implements InspectionToolProvider {\n" +
               "  public Class[] getInspectionClasses() {\n" +
               "    return new Class[] { CucumberStepInspection.class,\n" +
               "                         CucumberMissedExamplesInspection.class,\n" +
               "                         CucumberExamplesColonInspection.class\n" +
               "    <caret>};\n" +
               "  }\n" +
               "}");
  }


  public void testIdea155683() {
    doTextTest(
      "java",

      "package com.acme;\n" +
      "\n" +
      "public class Main {\n" +
      "    public static void main(String[] args) {\n" +
      "        if (true)\n" +
      "            System.out.println();\n" +
      "        else<caret>\n" +
      "        System.out.println();\n" +
      "    }\n" +
      "}",

      "package com.acme;\n" +
      "\n" +
      "public class Main {\n" +
      "    public static void main(String[] args) {\n" +
      "        if (true)\n" +
      "            System.out.println();\n" +
      "        else\n" +
      "            <caret>\n" +
      "        System.out.println();\n" +
      "    }\n" +
      "}"
    );
  }


  public void testBeforeBrace() {
    doTextTest(
      "java",

      "package com.acme;\n" +
      "\n" +
      "class Foo implements Bar,\n" +
      "                     Baz {\n" +
      "    void foo() {}\n" +
      "<caret>}",

      "package com.acme;\n" +
      "\n" +
      "class Foo implements Bar,\n" +
      "                     Baz {\n" +
      "    void foo() {}\n" +
      "\n" +
      "<caret>}"
    );
  }

  public void testBeforeBrace1() {
    doTextTest(
      "java",

      "package com.acme;\n" +
      "\n" +
      "class Foo {\n" +
      "    void foo() {\n" +
      "        \n" +
      "    <caret>}\n" +
      "<caret>}",

      "package com.acme;\n" +
      "\n" +
      "class Foo {\n" +
      "    void foo() {\n" +
      "        \n" +
      "    \n" +
      "    <caret>}\n" +
      "\n" +
      "<caret>}"
    );
  }


  public void testAfterBraceWithCommentBefore() {
    doTextTest(
      "java",

      "package com.acme;\n" +
      "\n" +
      "public class Test {\n" +
      "    protected boolean fix(String foo, String bar) {\n" +
      "        if (foo != null) { // That's a comment\n" +
      "            if (bar == null) {<caret>\n" +
      "            }\n" +
      "        }\n" +
      "        return true;\n" +
      "    }\n" +
      "}",

      "package com.acme;\n" +
      "\n" +
      "public class Test {\n" +
      "    protected boolean fix(String foo, String bar) {\n" +
      "        if (foo != null) { // That's a comment\n" +
      "            if (bar == null) {\n" +
      "                <caret>\n" +
      "            }\n" +
      "        }\n" +
      "        return true;\n" +
      "    }\n" +
      "}"
    );
  }


  public void testIdea159285() {
    doTextTest(
      "java",

      "package com.acme;\n" +
      "\n" +
      "public class Test {\n" +
      "    private void foo() {\n" +
      "        int foo = 1;\n" +
      "        switch (foo) {\n" +
      "            case 1:\n" +
      "                for (int i = 0; i < 10; ++i) {<caret>\n" +
      "                }\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "package com.acme;\n" +
      "\n" +
      "public class Test {\n" +
      "    private void foo() {\n" +
      "        int foo = 1;\n" +
      "        switch (foo) {\n" +
      "            case 1:\n" +
      "                for (int i = 0; i < 10; ++i) {\n" +
      "                    <caret>\n" +
      "                }\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea160103() {
    doTextTest(
      "java",

      "package com.company;\n" +
      "\n" +
      "class Test {\n" +
      "    void foo() {\n" +
      "        int[] ints = {\n" +
      "                1,\n" +
      "                2};<caret>\n" +
      "    }\n" +
      "}",

      "package com.company;\n" +
      "\n" +
      "class Test {\n" +
      "    void foo() {\n" +
      "        int[] ints = {\n" +
      "                1,\n" +
      "                2};\n" +
      "        <caret>\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea160104() {
    doTextTest(
      "java",

      "package com.company;\n" +
      "\n" +
      "class Test {\n" +
      "    void foo() {\n" +
      "        int[] ints = {<caret>1, 2};\n" +
      "    }\n" +
      "}",

      "package com.company;\n" +
      "\n" +
      "class Test {\n" +
      "    void foo() {\n" +
      "        int[] ints = {\n" +
      "                <caret>1, 2};\n" +
      "    }\n" +
      "}"
    );
  }

  public void testEnterDoesNotGenerateAsteriskInNonCommentContext() {
    doTextTest("java", "import java.util.List;\n" +
                       "\n" +
                       "class Calculator{\n" +
                       "  public int calculateSomething(List<Integer> input){\n" +
                       "    return 2\n" +
                       "    * input.stream().map(i -> {<caret>});\n" +
                       "  }\n" +
                       "}\n",
               "import java.util.List;\n" +
               "\n" +
               "class Calculator{\n" +
               "  public int calculateSomething(List<Integer> input){\n" +
               "    return 2\n" +
               "    * input.stream().map(i -> {\n" +
               "        \n" +
               "    });\n" +
               "  }\n" +
               "}\n");
  }

  public void testEnterInsideTryWithResources() {
    doTextTest("java",
               "public class Test {\n" +
               "    public static void main(String[] args) {\n" +
               "        try (Reader r1 = null; <caret>Reader r2 = null) {}\n" +
               "    }\n" +
               "}\n",
               "public class Test {\n" +
               "    public static void main(String[] args) {\n" +
               "        try (Reader r1 = null; \n" +
               "             Reader r2 = null) {}\n" +
               "    }\n" +
               "}\n");
  }

  public void testDontApplyCaseIndentAfterConditionalOperator() {
    doTextTest("java",
               "public class Test {\n" +
               "  private void foo(boolean condition) {\n" +
               "\n" +
               "    boolean x = condition ? bar(\n" +
               "      \"param\"\n" +
               "    ) : true;<caret>\n" +
               "  }\n" +
               "\n" +
               "  private boolean bar(String param) {\n" +
               "    return false;\n" +
               "  }\n" +
               "}",
               "public class Test {\n" +
               "  private void foo(boolean condition) {\n" +
               "\n" +
               "    boolean x = condition ? bar(\n" +
               "      \"param\"\n" +
               "    ) : true;\n" +
               "    <caret>\n" +
               "  }\n" +
               "\n" +
               "  private boolean bar(String param) {\n" +
               "    return false;\n" +
               "  }\n" +
               "}");
  }

  public void testEnterInCaseBlockWithComment() {
    doTextTest("java",
               "class Test {\n" +
               "      private void foo(String p) {\n" +
               "          switch (p) {\n" +
               "              case \"123\": //some comment about this case\n" +
               "                  if (false) {<caret>\n" +
               "                  }\n" +
               "                  break;\n" +
               "              default:\n" +
               "                  break;\n" +
               "          }\n" +
               "  \n" +
               "      }\n" +
               "  }",
               "class Test {\n" +
               "      private void foo(String p) {\n" +
               "          switch (p) {\n" +
               "              case \"123\": //some comment about this case\n" +
               "                  if (false) {\n" +
               "                      <caret>\n" +
               "                  }\n" +
               "                  break;\n" +
               "              default:\n" +
               "                  break;\n" +
               "          }\n" +
               "  \n" +
               "      }\n" +
               "  }");
  }

  public void testBetweenComments() {
    doTextTest("java",
               "/*\n" +
               " */<caret>/**\n" +
               " */\n" +
               "class C {}",
               "/*\n" +
               " */\n" +
               "<caret>/**\n" +
               " */\n" +
               "class C {}");
  }

  public void testTodoInLineComment() {
    doTextTest("java", "// todo some <caret>text", "// todo some \n//  <caret>text");
  }

  public void testMultiLineTodoInLineComment() {
    doTextTest("java", "// todo some text\n//  next <caret>line", "// todo some text\n//  next \n//  <caret>line");
  }

  public void testTodoInBlockComment() {
    doTextTest("java", "/* todo some <caret>text */", "/* todo some \n    <caret>text */");
  }

  public void testMultiLineTodoInBlockComment() {
    doTextTest("java", "/* todo some text\n    next <caret>line */", "/* todo some text\n    next \n    <caret>line */");
  }

  public void testNoBraceOnTheWrongPosition() {
    doTextTest(
      "java",

      "class Test {<caret>}\n" +
      "class TestIncomplete {",

      "class Test {\n" +
      "    <caret>\n" +
      "}\n" +
      "class TestIncomplete {"
    );
  }

  public void testSCR2024() {
    doTextTest("java", "class Foo {\n" + "    void foo() {\n" + "        switch (a) {\n" + "            case 1:<caret>\n" + "        }\n" +
                       "    }\n" + "}", "class Foo {\n" + "    void foo() {\n" + "        switch (a) {\n" + "            case 1:\n" +
                                        "                <caret>\n" + "        }\n" + "    }\n" + "}");
  }

  public void testSCR2124() {
    doTextTest("java", "class Foo {\n" + "    public final int f() { \n" + "        A:<caret>\n" + "        int i;\n" + "    }\n" + "}",
               "class Foo {\n" + "    public final int f() { \n" + "        A:\n" + "        <caret>\n" + "        int i;\n" + "    }\n" +
               "}");
  }
  public void testCStyleCommentCompletion() {

    doTextTest("java",

               "public class Foo {\n" + "    public void foo() {\n" + "        /*<caret>\n" + "    }\n",

               "public class Foo {\n" + "    public void foo() {\n" + "        /*\n" + "        <caret>\n" + "         */\n" + "    }\n");
  }

  public void testInsideCStyleComment() {
    doTextTest("java",

               "public class Foo {\n" + "    public void foo() {\n" + "        /*\n" + "         Some comment<caret>\n" + "         */\n" +
               "    }\n",

               "public class Foo {\n" + "    public void foo() {\n" + "        /*\n" + "         Some comment\n" + "         <caret>\n" +
               "         */\n" + "    }\n");
  }

  public void testInsideCStyleCommentWithStars() {
    doTextTest("java",

               "public class Foo {\n" + "    public void foo() {\n" + "        /*\n" + "         * Some comment<caret>\n" +
               "         */\n" + "    }\n",

               "public class Foo {\n" + "    public void foo() {\n" + "        /*\n" + "         * Some comment\n" +
               "         * <caret>\n" + "         */\n" + "    }\n");
  }

  protected void doTest() throws Exception {
    doTest("java");
  }

  public void testEnterInsideAnnotationParameters() throws IOException {
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;

    doTextTest("java", 
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class, <caret>)\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}", 
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class, \n" +
               "                <caret>)\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}");

    doTextTest("java",
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class, <caret>\n" +
               "  )\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}",
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class, \n" +
               "                <caret>\n" +
               "  )\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}");
  }

  public void testEnterInsideAnnotationParameters_AfterNameValuePairBeforeLparenth() throws IOException {
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;

    doTextTest("java",
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class<caret>)\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}",
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class\n" +
               "  <caret>)\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}");
  }

  public void testToCodeBlockLambda() throws Exception {
    doTextTest("java", "class Issue {\n" +
                       "public static void main(String[] args) {\n" +
                       "Arrays.asList().stream().collect(() -> {<caret> new ArrayList<>(), ArrayList::add, ArrayList::addAll);\n" +
                       "}\n" +
                       "}",
                        "class Issue {\n" +
                        "public static void main(String[] args) {\n" +
                        "Arrays.asList().stream().collect(() -> {\n" +
                        "    new ArrayList<>()\n" +
                        "}, ArrayList::add, ArrayList::addAll);\n" +
                        "}\n" +
                        "}");
  }

  public void testEnter_BetweenChainedMethodCalls() throws IOException {
    doTextTest("java",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                .theseChildrenArePullable(eventsListView)\n" +
               "                .listener(this)\n" +
               "                .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>\n" +
               "                .setup(mPullToRefreshLayout);\n" +
               "    }\n" +
               "}",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                .theseChildrenArePullable(eventsListView)\n" +
               "                .listener(this)\n" +
               "                .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())\n" +
               "                <caret>\n" +
               "                .setup(mPullToRefreshLayout);\n" +
               "    }\n" +
               "}");
  }
  
  public void testEnter_BetweenAlignedChainedMethodCalls() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_CHAINED_METHODS = true;

    doTextTest("java",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                              .theseChildrenArePullable(eventsListView)\n" +
               "                              .listener(this)\n" +
               "                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>\n" +
               "                              .setup(mPullToRefreshLayout);\n" +
               "    }\n" +
               "}",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                              .theseChildrenArePullable(eventsListView)\n" +
               "                              .listener(this)\n" +
               "                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())\n" +
               "                              <caret>\n" +
               "                              .setup(mPullToRefreshLayout);\n" +
               "    }\n" +
               "}");
  }
  
  public void testEnter_AfterLastChainedCall() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_CHAINED_METHODS = true;

    doTextTest("java",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                              .theseChildrenArePullable(eventsListView)\n" +
               "                              .listener(this)\n" +
               "                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>\n" +
               "    }\n" +
               "}",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                              .theseChildrenArePullable(eventsListView)\n" +
               "                              .listener(this)\n" +
               "                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())\n" +
               "                              <caret>\n" +
               "    }\n" +
               "}");
  }

  public void testEnter_NewArgumentWithTabs() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.getIndentOptions().USE_TAB_CHARACTER = true;
    javaCommon.getIndentOptions().SMART_TABS = true;

    doTextTest("java",
               "class T {\n" +
               "\tvoid test(\n" +
               "\t\t\tint a,<caret>\n" +
               ") {}",
               "class T {\n" +
               "\tvoid test(\n" +
               "\t\t\tint a,\n" +
               "\t\t\t<caret>\n" +
               ") {}");
  }

  public void testEnter_AfterStatementWithoutBlock() throws IOException {
    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            while (true) <caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            while (true) \n" +
               "                <caret>\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            while (true) {<caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            while (true) {\n" +
               "                <caret>\n" +
               "            }\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            try {<caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            try {\n" +
               "                <caret>\n" +
               "            }\n" +
               "    }\n" +
               "}\n");
  }

  public void testEnter_AfterStatementWithLabel() throws IOException {
    // as prev
    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "lb:\n" +
               "        while (true) break lb;<caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "lb:\n" +
               "        while (true) break lb;\n" +
               "        <caret>\n" +
               "    }\n" +
               "}\n");

    // as block
    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "lb:  while (true) break lb;<caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "lb:  while (true) break lb;\n" +
               "        <caret>\n" +
               "    }\n" +
               "}\n");
  }

  public void testEnter_inlineComment() throws IOException {
    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        /<caret>/\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        /\n" +
               "        <caret>/\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        <caret>//\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        \n" +
               "        <caret>//\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        //a<caret>b\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        //a\n" +
               "        // <caret>b\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        //<caret>",
               "class T {\n" +
               "    void test() {\n" +
               "        //\n" +
               "    <caret>");
    }  
  
  public void testEnter_NewArgumentWithTabsNoAlign() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.getIndentOptions().USE_TAB_CHARACTER = true;
    javaCommon.getIndentOptions().SMART_TABS = true;
    javaCommon.ALIGN_MULTILINE_PARAMETERS = false;

    doTextTest("java",
               "class T {\n" +
               "\tvoid test(\n" +
               "\t\t\tint a,<caret>\n" +
               ") {}",
               "class T {\n" +
               "\tvoid test(\n" +
               "\t\t\tint a,\n" +
               "\t\t\t<caret>\n" +
               ") {}");
  }

  public void testIdea179073() throws IOException {
    doTextTest("java",
               "ArrayList<String> strings = new ArrayList<>();\n" +
               "    strings.stream()\n" +
               "        .forEach((e) -> {<caret>\n" +
               "        });",

               "ArrayList<String> strings = new ArrayList<>();\n" +
               "    strings.stream()\n" +
               "        .forEach((e) -> {\n" +
               "            <caret>\n" +
               "        });");
  }

  public void testIdea187535() throws IOException {
    doTextTest(
      "java",

      "public class Main {\n" +
      "    void foo() {\n" +
      "        {\n" +
      "            int a = 1;\n" +
      "        }\n" +
      "        int b = 2;<caret>\n" +
      "    }\n" +
      "}"
      ,
      "public class Main {\n" +
      "    void foo() {\n" +
      "        {\n" +
      "            int a = 1;\n" +
      "        }\n" +
      "        int b = 2;\n" +
      "        <caret>\n" +
      "    }\n" +
      "}");
  }

  public void testIdea189059() throws IOException {
    doTextTest(
      "java",

      "public class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        String[] s =\n" +
      "                new String[] {<caret>};\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        String[] s =\n" +
      "                new String[] {\n" +
      "                        <caret>\n" +
      "                };\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea108112() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest(
      "java",

      "public class Test {\n" +
      "    public void bar() {\n" +
      "        boolean abc;\n" +
      "        while (abc &&<caret>) {\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    public void bar() {\n" +
      "        boolean abc;\n" +
      "        while (abc &&\n" +
      "               <caret>) {\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea153628() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest(
      "java",

      "public class Test {\n" +
      "    public boolean hasInvalidResults() {\n" +
      "        return foo ||<caret>;\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    public boolean hasInvalidResults() {\n" +
      "        return foo ||\n" +
      "               <caret>;\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea115696() throws IOException {
    doTextTest(
      "java",

      "class T {\n" +
      "    private void someMethod() {\n" +
      "        System.out.println(\"foo\" +<caret>);\n" +
      "    }\n" +
      "\n" +
      "}",

      "class T {\n" +
      "    private void someMethod() {\n" +
      "        System.out.println(\"foo\" +\n" +
      "                <caret>);\n" +
      "    }\n" +
      "\n" +
      "}"
    );
  }

  public void testIdea115696_Aligned() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest(
      "java",

      "class T {\n" +
      "    private void someMethod() {\n" +
      "        System.out.println(\"foo\" +<caret>);\n" +
      "    }\n" +
      "\n" +
      "}",

      "class T {\n" +
      "    private void someMethod() {\n" +
      "        System.out.println(\"foo\" +\n" +
      "                           <caret>);\n" +
      "    }\n" +
      "\n" +
      "}"
    );
  }

  public void testIdea198767() throws IOException {
    doTextTest(
      "java",

      "package com.company;\n" +
      "\n" +
      "public class SomeExample {\n" +
      "    void test() {\n" +
      "        for (int i = 0; i < 10; i++)\n" +
      "            for (int j = 0; j < 5; j++)\n" +
      "                for (int k = 0; k < 5; k++) {\n" +
      "                    System.out.println(\"Sum \" + (i + j + k));\n" +
      "                }<caret>\n" +
      "    }\n" +
      "}",

      "package com.company;\n" +
      "\n" +
      "public class SomeExample {\n" +
      "    void test() {\n" +
      "        for (int i = 0; i < 10; i++)\n" +
      "            for (int j = 0; j < 5; j++)\n" +
      "                for (int k = 0; k < 5; k++) {\n" +
      "                    System.out.println(\"Sum \" + (i + j + k));\n" +
      "                }\n" +
      "        <caret>\n" +
      "    }\n" +
      "}"
    );
  }

  public void testEnterPerformanceAfterDeepTree() {
    configureFromFileText("a.java", ("class Foo {\n" +
                                     "  {\n" +
                                     "    u." +
                                     StringUtil.repeat("\n      a('b').c(new Some()).", 500)) + "<caret>\n" +
                                    "      x(); } }");
    PlatformTestUtil.startPerformanceTest("enter", 1500, this::performAction).assertTiming();
  }

  public void testIdea181263() {
    doTextTest(
      "java",

      "package com.company;\n" +
      "\n" +
      "public class Test3 {\n" +
      "    public static void main(String[] args)\n" +
      "    {\n" +
      "/*\n" +
      "        System.out.println(\"Commented\");\n" +
      "*/\n" +
      "        <caret>System.out.println(\"Hello\");\n" +
      "    }\n" +
      "}",

      "package com.company;\n" +
      "\n" +
      "public class Test3 {\n" +
      "    public static void main(String[] args)\n" +
      "    {\n" +
      "/*\n" +
      "        System.out.println(\"Commented\");\n" +
      "*/\n" +
      "        \n" +
      "        <caret>System.out.println(\"Hello\");\n" +
      "    }\n" +
      "}");
  }

  public void testIdea235221() {
    doTextTest(
      "java",

      "package test;\n" +
      "\n" +
      "public class Crush {\n" +
      "    void crush() {\n" +
      "        assertThat()\n" +
      "                /* Then */\n" +
      "        .isNotNull()<caret>\n" +
      "    }\n" +
      "}",

      "package test;\n" +
      "\n" +
      "public class Crush {\n" +
      "    void crush() {\n" +
      "        assertThat()\n" +
      "                /* Then */\n" +
      "        .isNotNull()\n" +
      "                <caret>\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea192807() {
    doTextTest(
      "java",

      "class MyTest\n" +
      "{\n" +
      "    private void foo() { String a = \"a\";<caret> String b = \"b\";}\n" +
      "}",

      "class MyTest\n" +
      "{\n" +
      "    private void foo() { String a = \"a\";\n" +
      "        <caret>String b = \"b\";}\n" +
      "}"
    );
  }

  public void testIdea188397() {
    doTextTest(
      "java",

      "public class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        System.out.println(\"Hello World!\");}<caret>\n" +
      "}",

      "public class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        System.out.println(\"Hello World!\");}\n" +
      "    <caret>\n" +
      "}"
    );
  }

  public void testIdea163806() {
    doTextTest(
      "java",

      "public class Test {\n" +
      "    /**\n" +
      "     * Something<br><caret>\n" +
      "     */\n" +
      "    void foo() {\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    /**\n" +
      "     * Something<br>\n" +
      "     * <caret>\n" +
      "     */\n" +
      "    void foo() {\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea205999() {
    doTextTest(
      "java",

      "public class Test {\n" +
      "    void foo(String a, String b, String c)\n" +
      "    {\n" +
      "        if(true) \n" +
      "        {}\n" +
      "        else<caret>{}\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    void foo(String a, String b, String c)\n" +
      "    {\n" +
      "        if(true) \n" +
      "        {}\n" +
      "        else\n" +
      "        <caret>{}\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea160629() {
    CodeStyle.doWithTemporarySettings(
      getProject(),
      getCurrentCodeStyleSettings(),
      settings -> {
        settings.getCommonSettings(JavaLanguage.INSTANCE).DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = true;
        doTextTest(
          "java",

          "public class Test extends A implements B,C {<caret>\n" +
          "}",

          "public class Test extends A implements B,C {\n" +
          "<caret>\n" +
          "}"
        );
      });
  }

  public void testIdea205888() {
    doTextTest(
      "java",

      "class Test {\n" +
      "    void foo() {\n" +
      "        boolean value = true;\n" +
      "        if (value)\n" +
      "            if (value)\n" +
      "                value = false;<caret>\n" +
      "    }\n" +
      "}",

      "class Test {\n" +
      "    void foo() {\n" +
      "        boolean value = true;\n" +
      "        if (value)\n" +
      "            if (value)\n" +
      "                value = false;\n" +
      "        <caret>\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIfElseChain() {
    doTextTest(
      "java",
      "class X {\n" +
      "  void test(int x) {\n" +
      "    if(x > 0) {\n" +
      "    } else if(x == 0) {<caret>else {\n" +
      "    }\n" +
      "  }\n" +
      "}",
      "class X {\n" +
      "  void test(int x) {\n" +
      "    if(x > 0) {\n" +
      "    } else if(x == 0) {\n" +
      "        \n" +
      "    }else {\n" +
      "    }\n" +
      "  }\n" +
      "}"
    );
  }
}