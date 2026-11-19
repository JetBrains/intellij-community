// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
@IndexingModeTemplate
public class HippieCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testDollars() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.txt", """
        $some_long_variable_name = Obj::instance();
        $some_lon<caret>
        """);
      complete();
      myFixture.checkResult("""
                              $some_long_variable_name = Obj::instance();
                              $some_long_variable_name<caret>
                              """);
    });
  }

  @Test
  public void testLooping() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.txt", """
        String word = name.substring(wordStart, index);
        wor<caret>
        """);
      complete();
      myFixture.checkResult("""
                              String word = name.substring(wordStart, index);
                              wordStart<caret>
                              """);
      complete();
      myFixture.checkResult("""
                              String word = name.substring(wordStart, index);
                              word<caret>
                              """);
      complete();
      myFixture.checkResult("""
                              String word = name.substring(wordStart, index);
                              wor<caret>
                              """);
      complete();
      myFixture.checkResult("""
                              String word = name.substring(wordStart, index);
                              wordStart<caret>
                              """);
      complete();
      myFixture.checkResult("""
                              String word = name.substring(wordStart, index);
                              word<caret>
                              """);
    });
  }

  @Test
  public void testFromAnotherFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("b.txt", """
        $some_local = 1;
        """);
      myFixture.configureByText("a.txt", """
        $some_long_variable_name = Obj::instance();
        $some_lo<caret>
        """);

      complete();
      myFixture.checkResult("""
                              $some_long_variable_name = Obj::instance();
                              $some_long_variable_name<caret>
                              """);
      complete();
      myFixture.checkResult("""
                              $some_long_variable_name = Obj::instance();
                              $some_local<caret>
                              """);
      backComplete();
      myFixture.checkResult("""
                              $some_long_variable_name = Obj::instance();
                              $some_long_variable_name<caret>
                              """);
    });
  }

  @Test
  public void testFromAnotherFile2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("b.txt", """
        foo function foo2
        """);
      myFixture.configureByText("a.txt", """
        f<caret>
        """);

      complete();
      myFixture.checkResult("""
                              foo2<caret>
                              """);
      complete();
      myFixture.checkResult("""
                              function<caret>
                              """);
      complete();
      myFixture.checkResult("""
                              foo<caret>
                              """);
      myFixture.configureByText("a.txt", """
        f<caret>
        """);
      backComplete();
      myFixture.checkResult("""
                              foo<caret>
                              """);

      backComplete();
      myFixture.checkResult("""
                              function<caret>
                              """);
      backComplete();
      myFixture.checkResult("""
                              foo2<caret>
                              """);
    });
  }

  @Test
  public void testNoMiddleMatching() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.txt", """
        fooExpression
        exp<caret>
        """);
      complete();
      myFixture.checkResult("""
                              fooExpression
                              exp<caret>
                              """);
    });
  }

  @Test
  public void testWordsFromJavadoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        /** some comment */
        com<caret>
        """);
      complete();
      myFixture.checkResult("""
                              /** some comment */
                              comment<caret>
                              """);
    });
  }

  @Test
  public void testWordsFromLineComments() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        // some comment2
        com<caret>
        """);
      complete();
      myFixture.checkResult("""
                              // some comment2
                              comment2<caret>
                              """);
    });
  }

  @Test
  public void testWordsFromBlockComments() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        /* some comment3 */
        com<caret>
        """);
      complete();
      myFixture.checkResult("""
                              /* some comment3 */
                              comment3<caret>
                              """);
    });
  }

  @Test
  public void testCompleteInStringLiteral() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class Foo {
          public Collection<JetFile> allInScope(@NotNull GlobalSearchScope scope) {
            System.out.println("allInSco<caret>: " + scope);
          }
        }
        """);
      complete();
      myFixture.checkResult("""
                              class Foo {
                                public Collection<JetFile> allInScope(@NotNull GlobalSearchScope scope) {
                                  System.out.println("allInScope<caret>: " + scope);
                                }
                              }
                              """);
    });
  }

  @Test
  public void testCompleteVariableNameInStringLiteral() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class Xoo {
          String foobar = "foo<caret>";
        }
        """);
      complete();
      myFixture.checkResult("""
                              class Xoo {
                                String foobar = "foobar<caret>";
                              }
                              """);
    });
  }

  @Test
  public void testFileStart() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        <caret>
        class Xoo {
        }
        """);
      complete();
      myFixture.checkResult("""
                              class<caret>
                              class Xoo {
                              }
                              """);
    });
  }

  @Test
  public void testCppIndirection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.c", """
        f<caret>
        foo->bar
        """);
      complete();
      myFixture.checkResult("""
                              foo<caret>
                              foo->bar
                              """);
    });
  }

  @Test
  public void testNumbers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.c", """
        246<caret>
        24601
        """);
      complete();
      myFixture.checkResult("""
                              24601<caret>
                              24601
                              """);
    });
  }

  @Test
  public void testInsideWord() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.c", "foo fox f<caret>bar");
      complete();
      myFixture.checkResult("foo fox fox<caret>bar");
      complete();
      myFixture.checkResult("foo fox foo<caret>bar");
    });
  }

  @Test
  public void testMultipleCarets() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.txt", "fox food floor f<caret> f<caret>");
      complete();
      myFixture.checkResult("fox food floor floor<caret> floor<caret>");
      complete();
      myFixture.checkResult("fox food floor food<caret> food<caret>");
      complete();
      myFixture.checkResult("fox food floor fox<caret> fox<caret>");
    });
  }

  @Test
  public void testMultipleCaretsBackward() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.txt", "f<caret> f<caret> fox food floor");
      backComplete();
      myFixture.checkResult("fox<caret> fox<caret> fox food floor");
      backComplete();
      myFixture.checkResult("food<caret> food<caret> fox food floor");
      backComplete();
      myFixture.checkResult("floor<caret> floor<caret> fox food floor");
    });
  }

  private void complete() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION);
  }

  private void backComplete() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.performEditorAction(IdeActions.ACTION_HIPPIE_BACKWARD_COMPLETION);
  }
}
