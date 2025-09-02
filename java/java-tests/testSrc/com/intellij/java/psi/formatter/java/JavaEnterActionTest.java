// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AbstractBasicJavaEnterActionTest;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.LocalTimeCounter;

public class JavaEnterActionTest extends AbstractBasicJavaEnterActionTest {

  //almost all because of formatting
  private CommonCodeStyleSettings getJavaCommonSettings() {
    return CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
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


  public void testBinaryExpressionAsParameter() throws Exception {
    CommonCodeStyleSettings settings = getJavaCommonSettings();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
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

  public void testSplitLiteralInEscape() {
    configureByFile("/codeInsight/enterAction/splitLiteral/Escape.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/splitLiteral/Escape_after.java");
  }

  public void testJavaDocInlineTag() {
    configureByFile("/codeInsight/enterAction/javaDoc/beforeInlineTag.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/javaDoc/afterInlineTag.java");
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

  public void testNoCloseJavaDocComment() {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.CLOSE_COMMENT_ON_ENTER = false;
      doTextTest("java",
                 "/**<caret>",
                 "/**\n <caret>");
      return null;
    });
  }

  public void testNoSmartIndentInJavadoc() {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.SMART_INDENT_ON_ENTER = false;
      settings.JAVADOC_STUB_ON_ENTER = false;
      configureByFile("/codeInsight/enterAction/settings/NoJavadocStub.java");
      performAction();
      checkResultByFile(null, "/codeInsight/enterAction/settings/NoJavadocStub_after.java", true); // side effect...
      return null;
    });
  }

  public void testLineCommentAtTrailingSpaces() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "AtTrailingSpaces.java");
    performAction();
    checkResultByFile(path + "AtTrailingSpaces_after.java");
  }

  public void testNoJavadocStub() {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.JAVADOC_STUB_ON_ENTER = false;
      configureByFile("/codeInsight/enterAction/settings/NoJavadocStub.java");
      performAction();
      checkResultByFile("/codeInsight/enterAction/settings/NoJavadocStub_after.java");
      return null;
    });
  }


  public void testEnter_inlineComment() {
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

  public void testIdea115696_Aligned() {
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

  public void testSplitLiteral() {
    configureByFile("/codeInsight/enterAction/splitLiteral/Before.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/splitLiteral/After.java");
  }

  public void testWithinBracesWithSpaceAfterCaret() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "WithinBracesWithSpaceAfterCaret.java");
    performAction();
    checkResultByFile(path + "WithinBracesWithSpaceAfterCaret_after.java");
  }

  public void testSCR26493() {
    configureByFile("/codeInsight/enterAction/SCR26493/before1.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/SCR26493/after1.java");
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

  public void testAfterLbrace3() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "Before3.java");
    performAction();
    checkResultByFile(path + "After3.java");
  }


  public void testIdea108112() {
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

  public void testIdea115696() {
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

  public void testIdea153628() {
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

  public void testEnterInsideAnnotationParameters() {
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

  public void testEnterInsideAnnotationParameters_AfterNameValuePairBeforeLparenth() {
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

  public void testEnterAfterFirstAnnotationOfRecordComponent() {
    doTextTest("java",
      """
        record ExampleRecord(
                @MyAnno<caret>
                @MyAnno2
                String a) { }
        """,
      """
        record ExampleRecord(
                @MyAnno
                <caret>
                @MyAnno2
                String a) { }
        """
    );
  }

  public void testEnterAfterLastAnnotationOfRecordComponent() {
    doTextTest("java",
               """
                 record ExampleRecord(
                         @MyAnno
                         @MyAnno2<caret>
                         String a) { }
                 """,
               """
                 record ExampleRecord(
                         @MyAnno
                         @MyAnno2
                         <caret>
                         String a) { }
                 """
    );
  }

  public void testEnterBeforeAnnotationOfRecordComponent() {
    doTextTest("java",
               """
                 record ExampleRecord(<caret>
                         @MyAnno
                         @MyAnno2
                         String a) { }
                 """,
               """
                 record ExampleRecord(
                         <caret>
                         @MyAnno
                         @MyAnno2
                         String a) { }
                 """
    );
  }

  public void testEnterAfterIdentifierOfRecordComponent() {
    doTextTest("java",
               """
                 record ExampleRecord(
                         @MyAnno
                         @MyAnno2
                         String a,<caret>) { }
                 """,
               """
                 record ExampleRecord(
                         @MyAnno
                         @MyAnno2
                         String a,
                         <caret>) { }
                 """
    );
  }
}