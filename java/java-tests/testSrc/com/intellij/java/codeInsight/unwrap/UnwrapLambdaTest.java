// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class UnwrapLambdaTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    assertUnwrapped(codeBefore, expectedCodeAfter, 0);
  }

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter, int option) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), "A.java", createCode(codeBefore), createCode(expectedCodeAfter), option);
  }

  @Test
  public void testUnwrap() {
    assertUnwrapped("""
                      {
                          Runnable r = () -> {
                             Sys<caret>tem.gc();
                          }
                      }
                      """,

                    """
                      {
                          Sys<caret>tem.gc();
                      }
                      """);
  }

  @Test
  public void testUnwrapNestedLambda() {
    assertUnwrapped("""
                      {
                          bar(() -> bar(() -> Sys<caret>tem.gc()));
                      }
                      """,

                    """
                      {
                          bar(() -> Sys<caret>tem.gc());
                      }
                      """, 1);
  }
  
  @Test
  public void testUnwrapNestedLambda2() {
    assertUnwrapped("""
                      {
                          bar(() -> bar(() -> {
                                               Sys<caret>tem.gc();
                                               System.gc();
                                               }));
                      }
                      """,

                    """
                      {
                          bar(() -> {
                                               System.gc();
                                               System.gc();
                                               });
                      }
                      """, 1);
  }
  
  @Test
  public void testUnwrapExpressionDeclaration() {
    assertUnwrapped("""
                      {
                          interface I {int get();}    I i = () -> <caret>1;
                      }
                      """,

                    """
                      {
                          interface I {int get();}    I i = 1;
                      }
                      """);
  }

  @Test
  public void testUnwrapBlockDeclaration() {
    assertUnwrapped("""
                      {
                          interface I {int get();}    I i = () -> { return <caret>1;};
                      }
                      """,

                    """
                      {
                          interface I {int get();}    I i = 1;
                      }
                      """);
  }

  @Test
  public void testUnwrapAssignment() {
    assertUnwrapped("""
                      {
                          interface I {int get();}    void bar(I i) {}    I i = bar(() -> 1<caret>1);
                      }
                      """,

                    """
                      {
                          interface I {int get();}    void bar(I i) {}    I i = 11;
                      }
                      """, 1);
  }

  @Test
  public void testUnwrapAssignmentWithCodeBlock() {
    assertUnwrapped("""
                      {
                          Runnable r = () -> {in<caret>t i = 0;};
                      }
                      """,

                    """
                      {
                          int i = 0;
                      }
                      """);
  }

  @Test
  public void testUnwrapUnresolved() {
    assertUnwrapped("""
                      {
                          () -> <caret>null;
                      }
                      """,

                    """
                      {
                          null;
                      }
                      """);
  }

  private static String createCode(String codeBefore) {
    return "public class A {\n" +
           "    void foo() {\n" +
           UnwrapTestUtil.indentTwice(codeBefore) +
           "    }\n" +
           "    void bar(Runnable r){}\n" +
           "}";
  }
}