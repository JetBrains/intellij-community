// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class AbstractBasicJavaEnterActionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testMethodName;

  @BeforeEach
  void setUpEnterActionTest(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(PathJavaTestUtil.getCommunityJavaTestDataPath());
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  protected @NotNull CodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  protected @NotNull Project getProject() {
    return projectFixture.get();
  }

  protected @NotNull CodeStyleSettings getCodeStyleSettings() {
    return CodeStyle.getSettings(getProject());
  }

  protected void configureByFile(@NonNls @NotNull String filePath) {
    getFixture().configureByFile(filePath);
  }

  protected void checkResultByFile(@NonNls @NotNull String expectedFilePath) {
    getFixture().checkResultByFile(expectedFilePath);
  }

  protected void checkResultByFile(@NonNls @NotNull String expectedFilePath, boolean ignoreTrailingSpaces) {
    getFixture().checkResultByFile(expectedFilePath, ignoreTrailingSpaces);
  }

  protected void performAction() {
    EnterActionTestUtil.performAction(getFixture());
  }

  protected void doTextTest(@NonNls @NotNull String ext, @NonNls @NotNull String before, @NonNls @NotNull String after) {
    EnterActionTestUtil.doTextTest(getFixture(), ext, before, after);
  }

  protected void doTest(@NonNls @NotNull String ext) {
    EnterActionTestUtil.doTest(getFixture(), testMethodName, ext);
  }

  @Test
  public void testAfterLbrace1() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "Before1.java");
    performAction();
    checkResultByFile(path + "After1.java");
  }

  @Test
  public void testAfterLbrace2() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "Before2.java");
    performAction();
    checkResultByFile(path + "After2.java");
  }

  @Test
  public void testLineCommentSCR13247() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "SCR13247.java");
    performAction();
    checkResultByFile(path + "SCR13247_after.java");
  }

  @Test
  public void testLineCommentAtLineEnd() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "AtLineEnd.java");
    performAction();
    checkResultByFile(path + "AtLineEnd_after.java");
  }

  @Test
  public void testLineCommentBeforeAnother() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "BeforeAnother.java");
    performAction();
    checkResultByFile(path + "BeforeAnother_after.java");
  }

  @Test
  public void testLineCommentBeforeAnotherWithSpace() {
    String path = "/codeInsight/enterAction/lineComment/";

    configureByFile(path + "BeforeAnotherWithSpace.java");
    performAction();
    checkResultByFile(path + "BeforeAnotherWithSpace_after.java");
  }


  @Test
  public void testWithinBraces() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "WithinBraces.java");
    performAction();
    checkResultByFile(path + "WithinBraces_after.java");
  }

  @Test
  public void testWithinBracesWithSpaceBeforeCaret() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "WithinBracesWithSpaceBeforeCaret.java");
    performAction();
    checkResultByFile(path + "WithinBracesWithSpaceBeforeCaret_after.java");
  }


  @Test
  public void testAfterLbraceWithStatement1() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "LbraceWithStatement1.java");
    performAction();
    checkResultByFile(path + "LbraceWithStatement1_after.java");
  }

  @Test
  public void testAfterLbraceWithSemicolon() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "LbraceWithSemicolon.java");
    performAction();
    checkResultByFile(path + "LbraceWithSemicolon_after.java");
  }

  @Test
  public void testAfterLbraceWithRParen() {
    String path = "/codeInsight/enterAction/afterLbrace/";

    configureByFile(path + "LbraceWithRParen.java");
    performAction();
    checkResultByFile(path + "LbraceWithRParen_after.java");
  }

  @Test
  public void testBug1() {
    configureByFile("/codeInsight/enterAction/bug1/Before.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/bug1/After.java");
  }

  @Test
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


  @Test
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

  @Test
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

  @Test
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


  @Test
  public void testBeforeElse() {
    configureByFile("/codeInsight/enterAction/BeforeElse_before.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/BeforeElse_after.java");
  }

  @Test
  public void testSimpleEnter() throws Exception {
    doTest();
  }

  @Test
  public void testSimpleEnter2() throws Exception {
    doTest();
  }

  @Test
  public void testSCR1647() {
    CodeStyleSettings settings = getCodeStyleSettings();
    final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);
    options.USE_TAB_CHARACTER = true;
    options.SMART_TABS = true;
    options.INDENT_SIZE = 4;
    options.TAB_SIZE = 2;
    configureByFile("/codeInsight/enterAction/SCR1647.java");
    performAction();
    checkResultByFile("/codeInsight/enterAction/SCR1647_after.java");
  }

  @Test
  public void testComment() throws Exception {
    doTest();
  }

  @Test
  public void testSaveSpaceAfter() throws Exception {
    doTest();
  }

  @Test
  public void testEndOfClass() throws Exception {
    doTest();
  }

  @Test
  public void testInsideClassFile() throws Exception {
    doTest();
  }


  @Test
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

  @Test
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

  @Test
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


  @Test
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

  @Test
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

  @Test
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

  @Test
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


  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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


  @Test
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

  @Test
  public void testSecondLineOfJavadocParameterMarkdown() {
      doTextTest(
        "java",
        """
          class Test {
              /// @param i this is my description
              ///          that spreads multiple lines<caret>
              void test(int i) {}
          }""",
        """
          class Test {
              /// @param i this is my description
              ///          that spreads multiple lines
              ///          <caret>
              void test(int i) {}
          }"""
      );
    }

  @Test
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

  @Test
  public void testCommentAtFileEnd() {
    doTextTest("java", "/*<caret>", """
      /*
      <caret>
       */""");
  }

  @Test
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

  @Test
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


  @Test
  public void testEnumWithoutContinuation() {
    doTextTest("java",
               "public <caret>enum X {}",
               "public \n" +
               "enum X {}");
  }

  @Test
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

  @Test
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


  @Test
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


  @Test
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

  @Test
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


  @Test
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


  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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


  @Test
  public void testTodoInLineComment() {
    doTextTest("java", "// todo some <caret>text", "// todo some \n//  <caret>text");
  }

  @Test
  public void testMultiLineTodoInLineComment() {
    doTextTest("java", "// todo some text\n//  next <caret>line", "// todo some text\n//  next \n//  <caret>line");
  }

  @Test
  public void testTodoInBlockComment() {
    doTextTest("java", "/* todo some <caret>text */", "/* todo some \n    <caret>text */");
  }

  @Test
  public void testMultiLineTodoInBlockComment() {
    doTextTest("java", "/* todo some text\n    next <caret>line */", "/* todo some text\n    next \n    <caret>line */");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testToCodeBlockLambda() {
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

  @Test
  public void testEnter_BetweenChainedMethodCalls() {
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

  @Test
  public void testEnter_BetweenAlignedChainedMethodCalls() {
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

  @Test
  public void testEnter_AfterLastChainedCall() {
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

  @Test
  public void testEnter_NewArgumentWithTabs() {
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

  @Test
  public void testEnter_AfterStatementWithoutBlock() {
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

  @Test
  public void testEnter_AfterStatementWithLabel() {
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


  @Test
  public void testEnter_NewArgumentWithTabsNoAlign() {
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

  @Test
  public void testIdea179073() {
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

  @Test
  public void testIdea187535() {
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

  @Test
  public void testIdea189059() {
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


  @Test
  public void testIdea198767() {
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


  @Test
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

  @Test
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


  @Test
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

  @Test
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

  @Test
  public void testIdea160629() {
    CodeStyle.doWithTemporarySettings(
      getProject(),
      getCodeStyleSettings(),
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

  @Test
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

  @Test
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