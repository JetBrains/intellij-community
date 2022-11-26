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

    String text = """
      class Foo {
      \tint

      \t\t\t\t\tmyField;

      \tvoid foo (int i,

      \t          int j

      ) {
      \t}}""";
    PsiFileFactory factory = PsiFileFactory.getInstance(PsiManager.getInstance(getProject()).getProject());
    final PsiFile file = factory.createFileFromText("a.java", JavaFileType.INSTANCE, text, LocalTimeCounter.currentTime(), true);
    doGetIndentTest(file, 2, "\t\t\t\t\t");
    doGetIndentTest(file, 4, "\t");
    doGetIndentTest(file, 6, "\t          ");
  }

  public void _testSCR1488() {
    JavaCodeStyleSettings.getInstance(getProject()).JD_LEADING_ASTERISKS_ARE_ENABLED = false;
    doTextTest("java", """
                 class Foo {
                 /**<caret>
                     public int foo(int i, int j, int k) throws IOException {
                     }}""",
               """
                 class Foo {
                     /**
                      <caret>
                      @param i
                      @param j
                      @param k
                      @return
                      @throws IOException
                      */
                     public int foo(int i, int j, int k) throws IOException {
                     }}""");
  }

  public void testJavaDocSplitWhenLeadingAsterisksAreDisabled() throws Exception {
    JavaCodeStyleSettings.getInstance(getProject()).JD_LEADING_ASTERISKS_ARE_ENABLED = false;
    doTest();
  }

  public void testSCR1535() {
    doTextTest("java", """
      /**
      */<caret>
      class Foo{}""", """
                 /**
                 */
                 <caret>
                 class Foo{}""");

    doTextTest("java", """
                 class Foo {
                     /**
                     */<caret>
                     void foo() {}
                 }""",
               """
                 class Foo {
                     /**
                     */
                     <caret>
                     void foo() {}
                 }""");

    doTextTest("java", """
                 class Foo {
                     /**
                     */<caret>
                     abstract void foo();
                 }""",
               """
                 class Foo {
                     /**
                     */
                     <caret>
                     abstract void foo();
                 }""");

    doTextTest("java", """
                 class Foo {
                     /**
                     */<caret>
                     int myFoo;
                 }""",
               """
                 class Foo {
                     /**
                     */
                     <caret>
                     int myFoo;
                 }""");


  }

  public void testSCR3006() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).INDENT_CASE_FROM_SWITCH = false;
    doTextTest("java", """
      class Foo {
          void foo(){
              switch (foo) {\s
              case 1:\s
                  doSomething();<caret>\s
                  break;\s
        }     }
      }""", """
                 class Foo {
                     void foo(){
                         switch (foo) {\s
                         case 1:\s
                             doSomething();
                             <caret>
                             break;\s
                   }     }
                 }""");
  }
  public void testSCRblabla() {

    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).INDENT_CASE_FROM_SWITCH = true;

    doTextTest("java", """
                 class Foo {
                     void foo(){
                         switch (foo) {\s
                             case 1:\s
                                 doSomething();<caret>\s
                                 break;\s
                   }     }
                 }""",
               """
                 class Foo {
                     void foo(){
                         switch (foo) {\s
                             case 1:\s
                                 doSomething();
                                 <caret>
                                 break;\s
                   }     }
                 }""");

  }


  public void testSCR1692() {
    doTextTest("java", """
      public class TryFinallyCatch {
          public static void main(String[] args) {
              try {
                  System.out.println("Hello");
              }<caret>
              finally{\s
              }
          }
      }""", """
                 public class TryFinallyCatch {
                     public static void main(String[] args) {
                         try {
                             System.out.println("Hello");
                         }
                         <caret>
                         finally{\s
                         }
                     }
                 }""");
  }

  public void testSCR1696() {
    doTextTest("java", """
                 class Foo {
                     void foo() {
                         for(;;)<caret>
                         foo();    }
                 }""",
               """
                 class Foo {
                     void foo() {
                         for(;;)
                             <caret>
                         foo();    }
                 }""");

    doTextTest("java", """
                 class Foo {
                     void foo() {
                         for(;;) {<caret>
                         foo();}    }
                 }""",
               """
                 class Foo {
                     void foo() {
                         for(;;) {
                             <caret>
                         foo();}    }
                 }""");

    doTextTest("java", """
                 class Foo {
                     void foo() {
                         for(;;) <caret>{
                         foo();}    }
                 }""",
               """
                 class Foo {
                     void foo() {
                         for(;;)\s
                         <caret>{
                         foo();}    }
                 }""");

    doTextTest("java", """
                 class Foo {
                     void foo() {
                         for(;<caret>;) {
                         foo();}    }
                 }""",
               """
                 class Foo {
                     void foo() {
                         for(;
                                 <caret>;) {
                         foo();}    }
                 }""");

    doTextTest("java", """
                 class Foo {
                     void foo() {
                         for(;;)<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         for(;;)
                             <caret>
                     }
                 }""");

  }

  public void testSCR1698() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).KEEP_FIRST_COLUMN_COMMENT = false;
    doTextTest("java",
               """
                 class A {
                       void foo() {<caret>
                 /*
                     previousContent();
                 */
                        }

                 }""",
               """
                 class A {
                       void foo() {
                           <caret>
                 /*
                     previousContent();
                 */
                        }

                 }""");
  }

  public void testInsideParameterList() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;

    doTextTest("java", """
                 class Foo {
                     void foo() {
                         foo(1,<caret>);
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         foo(1,
                                 <caret>);
                     }
                 }""");

    doTextTest("java", """
                 class Foo {
                     void foo() {
                         foo(1,<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         foo(1,
                                 <caret>
                     }
                 }""");
  }
  public void testBlaBla() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;

    doTextTest("java", """
                 class Foo {
                     void foo() {
                         foo(1,<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         foo(1,
                             <caret>
                     }
                 }""");

  }

  public void testInsideFor() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_FOR = true;
    doTextTest("java", """
                 class Foo {
                     void foo() {
                         for(int i = 0;<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         for(int i = 0;
                             <caret>
                     }
                 }""");

    doTextTest("java",
               """
                 class Foo {
                     void foo() {
                         for(int i = 0;
                             i < 10;<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         for(int i = 0;
                             i < 10;
                             <caret>
                     }
                 }""");
  }

  public void testEnterInImplementsList() {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_EXTENDS_LIST = false;
    doTextTest("java", """
      class A implements B,<caret>
      {
      }""", """
                 class A implements B,
                         <caret>
                 {
                 }""");
  }

  public void testEnterBlaBla() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;
    doTextTest("java", """
                 class A implements B,<caret>
                 {
                 }""",
               """
                 class A implements B,
                                    <caret>
                 {
                 }""");

  }

  public void testInsideCodeBlock() {
    doTextTest("java", """
      class Foo{
          void foo() {
              int[] i = new int[] {1,2,3,4,5<caret>
              ,6,7,8}
          }
      }""", """
                 class Foo{
                     void foo() {
                         int[] i = new int[] {1,2,3,4,5
                                 <caret>
                         ,6,7,8}
                     }
                 }""");
  }

  public void testInsideAnonymousClass() {
    doTextTest("java", """
      class Foo{
          void foo() {
              Runnable i = new Runnable() {
                  public void foo1(){}
                  public void foo2(){}
                  public void foo3(){}
                  public void foo4(){}
                  <caret>
              }
          }
      }""", """
                 class Foo{
                     void foo() {
                         Runnable i = new Runnable() {
                             public void foo1(){}
                             public void foo2(){}
                             public void foo3(){}
                             public void foo4(){}
                            \s
                             <caret>
                         }
                     }
                 }""");
  }

  public void testEnterInConditionOperation() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTextTest("java", """
                 class Foo {
                     void foo () {
                         int var = condition ?<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo () {
                         int var = condition ?
                                   <caret>
                     }
                 }""");
  }

  public void testInsideIfCondition() throws Exception {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTextTest("java", """
      class Foo{
          void foo() {
              if(A != null &&
                 B != null &&<caret>
                ) {
              }
          }
      }""", """
                 class Foo{
                     void foo() {
                         if(A != null &&
                            B != null &&
                            <caret>
                           ) {
                         }
                     }
                 }""");
  }

  public void testInsideIfCondition_2() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest("java", """
      class Foo {
          void foo() {
              if (info.myTargetElement != null &&
                info.myElementAtPointer != null && <caret>info.myTargetElement != info.myElementAtPointer) {
              }
          }
      }""", """
                 class Foo {
                     void foo() {
                         if (info.myTargetElement != null &&
                           info.myElementAtPointer != null &&\s
                             <caret>info.myTargetElement != info.myElementAtPointer) {
                         }
                     }
                 }""");
  }

  public void testBreakingElseIfWithoutBraces() {
    // Inspired by IDEA-60304.
    doTextTest(
      "java",
      """
        class Foo {
            void test() {
                if (foo()) {
                } else {<caret> if (bar())
                    quux();
            }
        }""",
      """
        class Foo {
            void test() {
                if (foo()) {
                } else {
                    if (bar())
                        quux();
                }
            }
        }"""
    );
  }

  public void testBreakingElseIfWithBraces() {
    // Inspired by IDEA-60304.
    doTextTest(
      "java",
      """
        class Foo {
            void test() {
                if (foo()) {
                } else {<caret> if (bar()) {
                    quux();
                }
            }
        }""",
      """
        class Foo {
            void test() {
                if (foo()) {
                } else {
                    if (bar()) {
                        quux();
                    }
                }
            }
        }"""
    );
  }

  public void testSCR2238() {
    CodeStyleSettings settings = getCodeStyleSettings();
    settings.getCommonSettings(JavaLanguage.INSTANCE).BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTextTest("java", """
                 class Foo {
                     void foo() {
                         switch (a) {<caret>
                         }
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         switch (a) {
                             <caret>
                         }
                     }
                 }""");

    doTextTest("java", """
      class Foo {
          void foo() {
              switch (a) {
                  case 1:
                  {
                  }<caret>
              }
          }
      }""", """
                 class Foo {
                     void foo() {
                         switch (a) {
                             case 1:
                             {
                             }
                             <caret>
                         }
                     }
                 }""");


    doTextTest("java",

               """
                 class Foo {
                     void foo() {
                         switch (a) {
                             case 1:
                                 foo();<caret>
                         }
                     }
                 }""",

               """
                 class Foo {
                     void foo() {
                         switch (a) {
                             case 1:
                                 foo();
                                 <caret>
                         }
                     }
                 }""");


    doTextTest("java",

               """
                 class Foo {
                     void foo() {
                         switch (a) {
                             case 1:
                                 foo();
                                 break;<caret>
                         }
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         switch (a) {
                             case 1:
                                 foo();
                                 break;
                                 <caret>
                         }
                     }
                 }""");

    doTextTest("java",

               """
                 class Foo {
                     void foo() {
                         switch (a) {
                             case 1:
                                 foo();
                                 return;<caret>
                         }
                     }
                 }""",

               """
                 class Foo {
                     void foo() {
                         switch (a) {
                             case 1:
                                 foo();
                                 return;
                                 <caret>
                         }
                     }
                 }""");


  }


  public void testEnterAfterSecondAnnotation() {
    doTextTest("java", """
      @A
      @B<caret>
      class C{}""", """
                 @A
                 @B
                 <caret>
                 class C{}""");
  }

  public void testIncompleteBeginOfFile() {
    doTextTest("java",
               """
                 public class Test {
                   public void foo(){
                     if (a)<caret>""",
               """
                 public class Test {
                   public void foo(){
                     if (a)
                         <caret>""");
  }

  public void testAtEndOfFile() {
    doTextTest("java",
               """
                 public class Test {
                   public void foo(){
                   }
                        <caret>""",
               """
                 public class Test {
                   public void foo(){
                   }
                       \s
                   <caret>"""
    );
  }

  public void testStringLiteralAsReferenceExpression() {
    doTextTest("java",
               """
                 public class Test {
                   {
                     String q = "abcdef<caret>ghijkl".replaceAll("KEY", "key");
                   }
                 }""",

               """
                 public class Test {
                   {
                     String q = ("abcdef" +
                             "<caret>ghijkl").replaceAll("KEY", "key");
                   }
                 }"""
    );
  }

  public void testLineCommentInJavadoc() {
    doTextTest("java",
               """
                   abstract class Test {
                     /**<caret>Foo//bar */
                     public abstract void foo();
                   }\
                 """,

               """
                   abstract class Test {
                     /**
                      * <caret>Foo//bar */
                     public abstract void foo();
                   }\
                 """
    );
  }

  public void testLineCommentInBlock() {
    doTextTest("java",
               """
                   abstract class Test {
                     /*
                      * <caret>Foo//bar */
                     public abstract void foo();
                   }\
                 """,

               """
                   abstract class Test {
                     /*
                      *\s
                      * <caret>Foo//bar */
                     public abstract void foo();
                   }\
                 """
    );
  }

  public void testIDEADEV_28200() {
    doTextTest("java",
               """
                 class Foo {
                     public void context() {
                 \t\tint v = 0;<caret>
                 \t\tmyField += v;
                 \t}}""",
               """
                 class Foo {
                     public void context() {
                 \t\tint v = 0;
                         <caret>
                 \t\tmyField += v;
                 \t}}""");
  }

  public void testIndentStatementAfterIf() {
    doTextTest("java",
               """
                 class Foo {
                     void foo () {
                         if(blah==3)<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo () {
                         if(blah==3)
                             <caret>
                     }
                 }"""
    );
  }

  public void testIDEADEV_14102() {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTextTest("java",
               """
                 class Foo {
                     void foo () {
                         PsiSubstitutor s = aClass == null ?
                         PsiSubstitutor.EMPTY : <caret>
                     }
                 }""",
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
               """
                 class Foo {
                     void foo () {
                         try {}<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo () {
                         try {}
                         <caret>
                     }
                 }"""
    );

  }

  public void testTryCatch() {
    doTextTest("java",
               """
                 class Foo {
                     void foo() {
                         try {
                        \s
                         } catch () {<caret>
                     }
                 }""",
               """
                 class Foo {
                     void foo() {
                         try {
                        \s
                         } catch () {
                             <caret>
                         }
                     }
                 }""");
  }

  public void testEnterBetweenBracesAtJavadoc() {
    // Inspired by IDEA-61221
    doTextTest(
      "java",
      """
        /**
         *    class Foo {<caret>}
         */class Test {
        }""",
      """
        /**
         *    class Foo {
         *        <caret>
         *    }
         */class Test {
        }"""
    );
  }

  public void testEnterBetweenNestedJavadocTag() {
    doTextTest(
      "java",
      """
        /**
         *    <outer><inner><caret></inner></outer>
         */class Test {
        }""",
      """
        /**
         *    <outer><inner>
         *        <caret>
         *    </inner></outer>
         */class Test {
        }"""
    );
  }

  public void testIndentAfterStartJavadocTag() {
    doTextTest(
      "java",
      """
        /**
         *    <pre><caret>
         */class Test {
        }""",
      """
        /**
         *    <pre>
         *        <caret>
         */class Test {
        }"""
    );
  }

  public void testEnterAfterEmptyJavadocTagIsNotIndented() {
    // Inspired by IDEA-65031
    doTextTest(
      "java",
      """
        /**
         *    <p/><caret>
         */class Test {
        }""",
      """
        /**
         *    <p/>
         *    <caret>
         */class Test {
        }"""
    );
  }

  public void testEnterBetweenJavadocTagsProducesNewLine() {
    doTextTest(
      "java",
      """
        /**
         *    <pre><caret></pre>
         */class Test {
        }""",
      """
        /**
         *    <pre>
         *        <caret>
         *    </pre>
         */class Test {
        }"""
    );
  }

  public void testTextBetweenJavadocTagStartAndCaret() {
    doTextTest(
      "java",
      """
        /**
         *    <pre>a<caret></pre>
         */class Test {
        }""",
      """
        /**
         *    <pre>a
         *    <caret></pre>
         */class Test {
        }"""
    );
  }

  public void testTextBetweenCaretAndJavadocEndTag() {
    doTextTest(
      "java",
      """
        /**
         *    <pre><caret>text</pre>
         */class Test {
        }""",
      """
        /**
         *    <pre>
         *        <caret>text
         *    </pre>
         */class Test {
        }"""
    );
  }

  public void testInitBlockAtAnonymousInnerClass() {
    doTextTest(
      "java",
      """
        class Test {
            public void test() {
                new Foo(new Bar() {{<caret>);
            }
        }""",
      """
        class Test {
            public void test() {
                new Foo(new Bar() {{
                    <caret>
                }});
            }
        }"""
    );
  }

  public void testAfterWrappedNonFinishedMethodCallExpression() {
    // Inspired by IDEA-64989
    doTextTest(
      "java",
      """
        class Test {
            public void test() {
                new Foo()
                        .bar()<caret>
            }
        }""",
      """
        class Test {
            public void test() {
                new Foo()
                        .bar()
                        <caret>
            }
        }"""
    );
  }

  public void testDoNotGrabUnnecessaryEndDocCommentSymbols() {
    // Inspired by IDEA-64896
    doTextTest(
      "java",
      """
        /**<caret>
        public class BrokenAlignment {

            int foo() {
               return 1 */*comment*/ 1;
            }
        }""",
      """
        /**
         * <caret>
         */
        public class BrokenAlignment {

            int foo() {
               return 1 */*comment*/ 1;
            }
        }"""
    );
  }

  public void testAlignmentIndentAfterIncompleteImplementsBlock() {
    // Inspired by IDEA-65777
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = true;

    doTextTest(
      "java",
      """
        public abstract class BrokenAlignment
                implements Comparable,<caret> {

        }""",
      """
        public abstract class BrokenAlignment
                implements Comparable,
                           <caret>{

        }"""
    );
  }

  public void testFirstLineOfJavadocParameter() {
    // Inspired by IDEA-IDEA-70194
    doTextTest(
      "java",
      """
        abstract class Test {
            /**
             * @param i  this is my description<caret>
             */
             void test(int i) {
             }
        }""",
      """
        abstract class Test {
            /**
             * @param i  this is my description
             *           <caret>
             */
             void test(int i) {
             }
        }"""
    );
  }

  public void testSecondLineOfJavadocParameter() {
    // Inspired by IDEA-IDEA-70194
    doTextTest(
      "java",
      """
        abstract class Test {
            /**
             * @param i  this is my description
             *           that spreads multiple lines<caret>
             */
             void test(int i) {
             }
        }""",
      """
        abstract class Test {
            /**
             * @param i  this is my description
             *           that spreads multiple lines
             *           <caret>
             */
             void test(int i) {
             }
        }"""
    );
  }

  public void testInsideJavadocParameter() {
    // Inspired by IDEA-IDEA-70194
    doTextTest(
      "java",
      """
        abstract class Test {
            /**
             * @param i  this is my <caret>description
             */
             void test(int i) {
             }
        }""",
      """
        abstract class Test {
            /**
             * @param i  this is my\s
             *           <caret>description
             */
             void test(int i) {
             }
        }"""
    );
  }

  public void testBeforeJavadocParameter() {
    // Inspired by IDEA-IDEA-70194
    doTextTest(
      "java",
      """
        abstract class Test {
            /**
             * <caret>@param i  this is my description
             */
             void test(int i) {
             }
        }""",
      """
        abstract class Test {
            /**
             *\s
             * <caret>@param i  this is my description
             */
             void test(int i) {
             }
        }"""
    );
  }

  public void testInsideJavadocParameterWithCodeStyleToAvoidAlignment() {
    // Inspired by IDEA-75802
    JavaCodeStyleSettings.getInstance(getProject()).JD_ALIGN_PARAM_COMMENTS = false;
    doTextTest(
      "java",
      """
        abstract class Test {
            /**
             * @param i  this is my <caret>description
             */
             void test(int i) {
             }
        }""",
      """
        abstract class Test {
            /**
             * @param i  this is my\s
             * <caret>description
             */
             void test(int i) {
             }
        }"""
    );
  }

  public void testCommentAtFileEnd() {
    doTextTest("java", "/*<caret>", """
      /*
      <caret>
       */""");
  }

  public void testEnterAfterIfCondition() {
    String before = """
      public class Test {
          public void main() {
             if (true){<caret> return;
             System.out.println("!");
          }
      }""";
    String after = """
      public class Test {
          public void main() {
             if (true){
                 return;
             }
             System.out.println("!");
          }
      }""";
    doTextTest("java", before, after);
  }

  public void testNoneIndentAfterMethodAnnotation() {
    String before = """
      class Test {
          @Get<caret>
          void test() {
          }
      }""";
    String after = """
      class Test {
          @Get
          <caret>
          void test() {
          }
      }""";
    doTextTest("java", before, after);
  }

  public void testAfterDocComment() {
    doTextTest("java",
               """
                 class Test {
                     /*
                      * */<caret>void m() {}
                 }""",
               """
                 class Test {
                     /*
                      * */
                     <caret>void m() {}
                 }""");
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
               """
                 public class CucumberInspectionsProvider implements InspectionToolProvider {
                   public Class[] getInspectionClasses() {
                     return new Class[] { CucumberStepInspection.class,
                                          CucumberMissedExamplesInspection.class,
                                          CucumberExamplesColonInspection.class, <caret>
                     };
                   }
                 }""",
               """
                 public class CucumberInspectionsProvider implements InspectionToolProvider {
                   public Class[] getInspectionClasses() {
                     return new Class[] { CucumberStepInspection.class,
                                          CucumberMissedExamplesInspection.class,
                                          CucumberExamplesColonInspection.class,\s
                                          <caret>
                     };
                   }
                 }""");

    doTextTest("java",
               """
                 public class CucumberInspectionsProvider implements InspectionToolProvider {
                   public Class[] getInspectionClasses() {
                     return new Class[] { CucumberStepInspection.class,
                                          CucumberMissedExamplesInspection.class,
                                          CucumberExamplesColonInspection.class, <caret>};
                   }
                 }""",
               """
                 public class CucumberInspectionsProvider implements InspectionToolProvider {
                   public Class[] getInspectionClasses() {
                     return new Class[] { CucumberStepInspection.class,
                                          CucumberMissedExamplesInspection.class,
                                          CucumberExamplesColonInspection.class,\s
                                          <caret>};
                   }
                 }""");
  }

  public void testEnterInArrayDeclaration_BeforeRBrace() {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = true;

    doTextTest("java",
               """
                 public class CucumberInspectionsProvider implements InspectionToolProvider {
                   public Class[] getInspectionClasses() {
                     return new Class[] { CucumberStepInspection.class,
                                          CucumberMissedExamplesInspection.class,
                                          CucumberExamplesColonInspection.class<caret>};
                   }
                 }""",
               """
                 public class CucumberInspectionsProvider implements InspectionToolProvider {
                   public Class[] getInspectionClasses() {
                     return new Class[] { CucumberStepInspection.class,
                                          CucumberMissedExamplesInspection.class,
                                          CucumberExamplesColonInspection.class
                     <caret>};
                   }
                 }""");
  }


  public void testIdea155683() {
    doTextTest(
      "java",

      """
        package com.acme;

        public class Main {
            public static void main(String[] args) {
                if (true)
                    System.out.println();
                else<caret>
                System.out.println();
            }
        }""",

      """
        package com.acme;

        public class Main {
            public static void main(String[] args) {
                if (true)
                    System.out.println();
                else
                    <caret>
                System.out.println();
            }
        }"""
    );
  }


  public void testBeforeBrace() {
    doTextTest(
      "java",

      """
        package com.acme;

        class Foo implements Bar,
                             Baz {
            void foo() {}
        <caret>}""",

      """
        package com.acme;

        class Foo implements Bar,
                             Baz {
            void foo() {}

        <caret>}"""
    );
  }

  public void testBeforeBrace1() {
    doTextTest(
      "java",

      """
        package com.acme;

        class Foo {
            void foo() {
               \s
            <caret>}
        <caret>}""",

      """
        package com.acme;

        class Foo {
            void foo() {
               \s
           \s
            <caret>}

        <caret>}"""
    );
  }


  public void testAfterBraceWithCommentBefore() {
    doTextTest(
      "java",

      """
        package com.acme;

        public class Test {
            protected boolean fix(String foo, String bar) {
                if (foo != null) { // That's a comment
                    if (bar == null) {<caret>
                    }
                }
                return true;
            }
        }""",

      """
        package com.acme;

        public class Test {
            protected boolean fix(String foo, String bar) {
                if (foo != null) { // That's a comment
                    if (bar == null) {
                        <caret>
                    }
                }
                return true;
            }
        }"""
    );
  }


  public void testIdea159285() {
    doTextTest(
      "java",

      """
        package com.acme;

        public class Test {
            private void foo() {
                int foo = 1;
                switch (foo) {
                    case 1:
                        for (int i = 0; i < 10; ++i) {<caret>
                        }
                }
            }
        }""",

      """
        package com.acme;

        public class Test {
            private void foo() {
                int foo = 1;
                switch (foo) {
                    case 1:
                        for (int i = 0; i < 10; ++i) {
                            <caret>
                        }
                }
            }
        }"""
    );
  }

  public void testIdea160103() {
    doTextTest(
      "java",

      """
        package com.company;

        class Test {
            void foo() {
                int[] ints = {
                        1,
                        2};<caret>
            }
        }""",

      """
        package com.company;

        class Test {
            void foo() {
                int[] ints = {
                        1,
                        2};
                <caret>
            }
        }"""
    );
  }

  public void testIdea160104() {
    doTextTest(
      "java",

      """
        package com.company;

        class Test {
            void foo() {
                int[] ints = {<caret>1, 2};
            }
        }""",

      """
        package com.company;

        class Test {
            void foo() {
                int[] ints = {
                        <caret>1, 2};
            }
        }"""
    );
  }

  public void testEnterDoesNotGenerateAsteriskInNonCommentContext() {
    doTextTest("java", """
                 import java.util.List;

                 class Calculator{
                   public int calculateSomething(List<Integer> input){
                     return 2
                     * input.stream().map(i -> {<caret>});
                   }
                 }
                 """,
               """
                 import java.util.List;

                 class Calculator{
                   public int calculateSomething(List<Integer> input){
                     return 2
                     * input.stream().map(i -> {
                        \s
                     });
                   }
                 }
                 """);
  }

  public void testEnterInsideTryWithResources() {
    doTextTest("java",
               """
                 public class Test {
                     public static void main(String[] args) {
                         try (Reader r1 = null; <caret>Reader r2 = null) {}
                     }
                 }
                 """,
               """
                 public class Test {
                     public static void main(String[] args) {
                         try (Reader r1 = null;\s
                              Reader r2 = null) {}
                     }
                 }
                 """);
  }

  public void testDontApplyCaseIndentAfterConditionalOperator() {
    doTextTest("java",
               """
                 public class Test {
                   private void foo(boolean condition) {

                     boolean x = condition ? bar(
                       "param"
                     ) : true;<caret>
                   }

                   private boolean bar(String param) {
                     return false;
                   }
                 }""",
               """
                 public class Test {
                   private void foo(boolean condition) {

                     boolean x = condition ? bar(
                       "param"
                     ) : true;
                     <caret>
                   }

                   private boolean bar(String param) {
                     return false;
                   }
                 }""");
  }

  public void testEnterInCaseBlockWithComment() {
    doTextTest("java",
               """
                 class Test {
                       private void foo(String p) {
                           switch (p) {
                               case "123": //some comment about this case
                                   if (false) {<caret>
                                   }
                                   break;
                               default:
                                   break;
                           }
                  \s
                       }
                   }""",
               """
                 class Test {
                       private void foo(String p) {
                           switch (p) {
                               case "123": //some comment about this case
                                   if (false) {
                                       <caret>
                                   }
                                   break;
                               default:
                                   break;
                           }
                  \s
                       }
                   }""");
  }

  public void testBetweenComments() {
    doTextTest("java",
               """
                 /*
                  */<caret>/**
                  */
                 class C {}""",
               """
                 /*
                  */
                 <caret>/**
                  */
                 class C {}""");
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

      """
        class Test {
            <caret>
        }
        class TestIncomplete {"""
    );
  }

  public void testSCR2024() {
    doTextTest("java", """
      class Foo {
          void foo() {
              switch (a) {
                  case 1:<caret>
              }
          }
      }""", """
                 class Foo {
                     void foo() {
                         switch (a) {
                             case 1:
                                 <caret>
                         }
                     }
                 }""");
  }

  public void testSCR2124() {
    doTextTest("java", """
                 class Foo {
                     public final int f() {\s
                         A:<caret>
                         int i;
                     }
                 }""",
               """
                 class Foo {
                     public final int f() {\s
                         A:
                         <caret>
                         int i;
                     }
                 }""");
  }
  public void testCStyleCommentCompletion() {

    doTextTest("java",

               """
                 public class Foo {
                     public void foo() {
                         /*<caret>
                     }
                 """,

               """
                 public class Foo {
                     public void foo() {
                         /*
                         <caret>
                          */
                     }
                 """);
  }

  public void testInsideCStyleComment() {
    doTextTest("java",

               """
                 public class Foo {
                     public void foo() {
                         /*
                          Some comment<caret>
                          */
                     }
                 """,

               """
                 public class Foo {
                     public void foo() {
                         /*
                          Some comment
                          <caret>
                          */
                     }
                 """);
  }

  public void testInsideCStyleCommentWithStars() {
    doTextTest("java",

               """
                 public class Foo {
                     public void foo() {
                         /*
                          * Some comment<caret>
                          */
                     }
                 """,

               """
                 public class Foo {
                     public void foo() {
                         /*
                          * Some comment
                          * <caret>
                          */
                     }
                 """);
  }

  protected void doTest() throws Exception {
    doTest("java");
  }

  public void testEnterInsideAnnotationParameters() throws IOException {
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;

    doTextTest("java",
               """
                 public class T {

                   @Configurable(order = 25,\s
                                 validator = BigDecimalPercentValidator.class, <caret>)
                   public void run() {
                   }
                  \s
                  \s
                 }""",
               """
                 public class T {

                   @Configurable(order = 25,\s
                                 validator = BigDecimalPercentValidator.class,\s
                                 <caret>)
                   public void run() {
                   }
                  \s
                  \s
                 }""");

    doTextTest("java",
               """
                 public class T {

                   @Configurable(order = 25,\s
                                 validator = BigDecimalPercentValidator.class, <caret>
                   )
                   public void run() {
                   }
                  \s
                  \s
                 }""",
               """
                 public class T {

                   @Configurable(order = 25,\s
                                 validator = BigDecimalPercentValidator.class,\s
                                 <caret>
                   )
                   public void run() {
                   }
                  \s
                  \s
                 }""");
  }

  public void testEnterInsideAnnotationParameters_AfterNameValuePairBeforeLparenth() throws IOException {
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;

    doTextTest("java",
               """
                 public class T {

                   @Configurable(order = 25,\s
                                 validator = BigDecimalPercentValidator.class<caret>)
                   public void run() {
                   }
                  \s
                  \s
                 }""",
               """
                 public class T {

                   @Configurable(order = 25,\s
                                 validator = BigDecimalPercentValidator.class
                   <caret>)
                   public void run() {
                   }
                  \s
                  \s
                 }""");
  }

  public void testToCodeBlockLambda() throws Exception {
    doTextTest("java", """
                 class Issue {
                 public static void main(String[] args) {
                 Arrays.asList().stream().collect(() -> {<caret> new ArrayList<>(), ArrayList::add, ArrayList::addAll);
                 }
                 }""",
               """
                 class Issue {
                 public static void main(String[] args) {
                 Arrays.asList().stream().collect(() -> {
                     new ArrayList<>()
                 }, ArrayList::add, ArrayList::addAll);
                 }
                 }""");
  }

  public void testEnter_BetweenChainedMethodCalls() throws IOException {
    doTextTest("java",
               """
                 class T {
                     public void main() {
                         ActionBarPullToRefresh.from(getActivity())
                                 .theseChildrenArePullable(eventsListView)
                                 .listener(this)
                                 .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>
                                 .setup(mPullToRefreshLayout);
                     }
                 }""",
               """
                 class T {
                     public void main() {
                         ActionBarPullToRefresh.from(getActivity())
                                 .theseChildrenArePullable(eventsListView)
                                 .listener(this)
                                 .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())
                                 <caret>
                                 .setup(mPullToRefreshLayout);
                     }
                 }""");
  }
  
  public void testEnter_BetweenAlignedChainedMethodCalls() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_CHAINED_METHODS = true;

    doTextTest("java",
               """
                 class T {
                     public void main() {
                         ActionBarPullToRefresh.from(getActivity())
                                               .theseChildrenArePullable(eventsListView)
                                               .listener(this)
                                               .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>
                                               .setup(mPullToRefreshLayout);
                     }
                 }""",
               """
                 class T {
                     public void main() {
                         ActionBarPullToRefresh.from(getActivity())
                                               .theseChildrenArePullable(eventsListView)
                                               .listener(this)
                                               .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())
                                               <caret>
                                               .setup(mPullToRefreshLayout);
                     }
                 }""");
  }
  
  public void testEnter_AfterLastChainedCall() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_CHAINED_METHODS = true;

    doTextTest("java",
               """
                 class T {
                     public void main() {
                         ActionBarPullToRefresh.from(getActivity())
                                               .theseChildrenArePullable(eventsListView)
                                               .listener(this)
                                               .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>
                     }
                 }""",
               """
                 class T {
                     public void main() {
                         ActionBarPullToRefresh.from(getActivity())
                                               .theseChildrenArePullable(eventsListView)
                                               .listener(this)
                                               .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())
                                               <caret>
                     }
                 }""");
  }

  public void testEnter_NewArgumentWithTabs() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.getIndentOptions().USE_TAB_CHARACTER = true;
    javaCommon.getIndentOptions().SMART_TABS = true;

    doTextTest("java",
               """
                 class T {
                 \tvoid test(
                 \t\t\tint a,<caret>
                 ) {}""",
               """
                 class T {
                 \tvoid test(
                 \t\t\tint a,
                 \t\t\t<caret>
                 ) {}""");
  }

  public void testEnter_AfterStatementWithoutBlock() throws IOException {
    doTextTest("java",
               """
                 class T {
                     void test() {
                         if (true)
                             while (true) <caret>
                     }
                 }
                 """,
               """
                 class T {
                     void test() {
                         if (true)
                             while (true)\s
                                 <caret>
                     }
                 }
                 """);

    doTextTest("java",
               """
                 class T {
                     void test() {
                         if (true)
                             while (true) {<caret>
                     }
                 }
                 """,
               """
                 class T {
                     void test() {
                         if (true)
                             while (true) {
                                 <caret>
                             }
                     }
                 }
                 """);

    doTextTest("java",
               """
                 class T {
                     void test() {
                         if (true)
                             try {<caret>
                     }
                 }
                 """,
               """
                 class T {
                     void test() {
                         if (true)
                             try {
                                 <caret>
                             }
                     }
                 }
                 """);
  }

  public void testEnter_AfterStatementWithLabel() throws IOException {
    // as prev
    doTextTest("java",
               """
                 class T {
                     void test() {
                 lb:
                         while (true) break lb;<caret>
                     }
                 }
                 """,
               """
                 class T {
                     void test() {
                 lb:
                         while (true) break lb;
                         <caret>
                     }
                 }
                 """);

    // as block
    doTextTest("java",
               """
                 class T {
                     void test() {
                 lb:  while (true) break lb;<caret>
                     }
                 }
                 """,
               """
                 class T {
                     void test() {
                 lb:  while (true) break lb;
                         <caret>
                     }
                 }
                 """);
  }

  public void testEnter_inlineComment() throws IOException {
    doTextTest("java",
               """
                 class T {
                     void test() {
                         /<caret>/
                     }
                 }
                 """,
               """
                 class T {
                     void test() {
                         /
                         <caret>/
                     }
                 }
                 """);

    doTextTest("java",
               """
                 class T {
                     void test() {
                         <caret>//
                     }
                 }
                 """,
               """
                 class T {
                     void test() {
                        \s
                         <caret>//
                     }
                 }
                 """);

    doTextTest("java",
               """
                 class T {
                     void test() {
                         //a<caret>b
                     }
                 }
                 """,
               """
                 class T {
                     void test() {
                         //a
                         // <caret>b
                     }
                 }
                 """);

    doTextTest("java",
               """
                 class T {
                     void test() {
                         //<caret>""",
               """
                 class T {
                     void test() {
                         //
                     <caret>""");
    }  
  
  public void testEnter_NewArgumentWithTabsNoAlign() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.getIndentOptions().USE_TAB_CHARACTER = true;
    javaCommon.getIndentOptions().SMART_TABS = true;
    javaCommon.ALIGN_MULTILINE_PARAMETERS = false;

    doTextTest("java",
               """
                 class T {
                 \tvoid test(
                 \t\t\tint a,<caret>
                 ) {}""",
               """
                 class T {
                 \tvoid test(
                 \t\t\tint a,
                 \t\t\t<caret>
                 ) {}""");
  }

  public void testIdea179073() throws IOException {
    doTextTest("java",
               """
                 ArrayList<String> strings = new ArrayList<>();
                     strings.stream()
                         .forEach((e) -> {<caret>
                         });""",

               """
                 ArrayList<String> strings = new ArrayList<>();
                     strings.stream()
                         .forEach((e) -> {
                             <caret>
                         });""");
  }

  public void testIdea187535() throws IOException {
    doTextTest(
      "java",

      """
        public class Main {
            void foo() {
                {
                    int a = 1;
                }
                int b = 2;<caret>
            }
        }"""
      ,
      """
        public class Main {
            void foo() {
                {
                    int a = 1;
                }
                int b = 2;
                <caret>
            }
        }""");
  }

  public void testIdea189059() throws IOException {
    doTextTest(
      "java",

      """
        public class Test {
            public static void main(String[] args) {
                String[] s =
                        new String[] {<caret>};
            }
        }""",

      """
        public class Test {
            public static void main(String[] args) {
                String[] s =
                        new String[] {
                                <caret>
                        };
            }
        }"""
    );
  }

  public void testIdea108112() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest(
      "java",

      """
        public class Test {
            public void bar() {
                boolean abc;
                while (abc &&<caret>) {
                }
            }
        }""",

      """
        public class Test {
            public void bar() {
                boolean abc;
                while (abc &&
                       <caret>) {
                }
            }
        }"""
    );
  }

  public void testIdea153628() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest(
      "java",

      """
        public class Test {
            public boolean hasInvalidResults() {
                return foo ||<caret>;
            }
        }""",

      """
        public class Test {
            public boolean hasInvalidResults() {
                return foo ||
                       <caret>;
            }
        }"""
    );
  }

  public void testIdea115696() throws IOException {
    doTextTest(
      "java",

      """
        class T {
            private void someMethod() {
                System.out.println("foo" +<caret>);
            }

        }""",

      """
        class T {
            private void someMethod() {
                System.out.println("foo" +
                        <caret>);
            }

        }"""
    );
  }

  public void testIdea115696_Aligned() throws IOException {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;

    doTextTest(
      "java",

      """
        class T {
            private void someMethod() {
                System.out.println("foo" +<caret>);
            }

        }""",

      """
        class T {
            private void someMethod() {
                System.out.println("foo" +
                                   <caret>);
            }

        }"""
    );
  }

  public void testIdea198767() throws IOException {
    doTextTest(
      "java",

      """
        package com.company;

        public class SomeExample {
            void test() {
                for (int i = 0; i < 10; i++)
                    for (int j = 0; j < 5; j++)
                        for (int k = 0; k < 5; k++) {
                            System.out.println("Sum " + (i + j + k));
                        }<caret>
            }
        }""",

      """
        package com.company;

        public class SomeExample {
            void test() {
                for (int i = 0; i < 10; i++)
                    for (int j = 0; j < 5; j++)
                        for (int k = 0; k < 5; k++) {
                            System.out.println("Sum " + (i + j + k));
                        }
                <caret>
            }
        }"""
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

      """
        package com.company;

        public class Test3 {
            public static void main(String[] args)
            {
        /*
                System.out.println("Commented");
        */
                <caret>System.out.println("Hello");
            }
        }""",

      """
        package com.company;

        public class Test3 {
            public static void main(String[] args)
            {
        /*
                System.out.println("Commented");
        */
               \s
                <caret>System.out.println("Hello");
            }
        }""");
  }

  public void testIdea235221() {
    doTextTest(
      "java",

      """
        package test;

        public class Crush {
            void crush() {
                assertThat()
                        /* Then */
                .isNotNull()<caret>
            }
        }""",

      """
        package test;

        public class Crush {
            void crush() {
                assertThat()
                        /* Then */
                .isNotNull()
                        <caret>
            }
        }"""
    );
  }

  public void testIdea192807() {
    doTextTest(
      "java",

      """
        class MyTest
        {
            private void foo() { String a = "a";<caret> String b = "b";}
        }""",

      """
        class MyTest
        {
            private void foo() { String a = "a";
                <caret>String b = "b";}
        }"""
    );
  }

  public void testIdea188397() {
    doTextTest(
      "java",

      """
        public class Test {
            public static void main(String[] args) {
                System.out.println("Hello World!");}<caret>
        }""",

      """
        public class Test {
            public static void main(String[] args) {
                System.out.println("Hello World!");}
            <caret>
        }"""
    );
  }

  public void testIdea163806() {
    doTextTest(
      "java",

      """
        public class Test {
            /**
             * Something<br><caret>
             */
            void foo() {
            }
        }""",

      """
        public class Test {
            /**
             * Something<br>
             * <caret>
             */
            void foo() {
            }
        }"""
    );
  }

  public void testIdea205999() {
    doTextTest(
      "java",

      """
        public class Test {
            void foo(String a, String b, String c)
            {
                if(true)\s
                {}
                else<caret>{}
            }
        }""",

      """
        public class Test {
            void foo(String a, String b, String c)
            {
                if(true)\s
                {}
                else
                <caret>{}
            }
        }"""
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

          """
            public class Test extends A implements B,C {
            <caret>
            }"""
        );
      });
  }

  public void testIdea205888() {
    doTextTest(
      "java",

      """
        class Test {
            void foo() {
                boolean value = true;
                if (value)
                    if (value)
                        value = false;<caret>
            }
        }""",

      """
        class Test {
            void foo() {
                boolean value = true;
                if (value)
                    if (value)
                        value = false;
                <caret>
            }
        }"""
    );
  }

  public void testIfElseChain() {
    doTextTest(
      "java",
      """
        class X {
          void test(int x) {
            if(x > 0) {
            } else if(x == 0) {<caret>else {
            }
          }
        }""",
      """
        class X {
          void test(int x) {
            if(x > 0) {
            } else if(x == 0) {
               \s
            }else {
            }
          }
        }"""
    );
  }
}