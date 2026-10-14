// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class UnwrapTryTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, 0);
  }

  private void assertOptions(String code, String... expectedOptions) {
    UnwrapTestUtil.assertOptions(codeInsightFixture.get(), code, expectedOptions);
  }

  @Test
  public void testTryEmpty() {
    assertUnwrapped("""
                      {
                          try {
                              <caret>
                          } catch(Exception e) {}
                      }
                      """,

                    """
                      {
                      <caret>}
                      """);
  }

  @Test
  public void testTryInsideIfWithoutBraces() {
    assertUnwrapped("""
                      {
                          if (true) try {
                              <caret>System.out.println();
                              System.out.println();
                          } catch(Exception e) {}
                      }
                      """,

                    """
                      {
                          if (true) {
                              <caret>System.out.println();
                              System.out.println();
                          }
                      }
                      """);
  }

  @Test
  public void testTryWithStatements() {
    assertUnwrapped("""
                      try {
                          int i;
                          int j;<caret>
                      } catch(Exception e) {}
                      """,

                    """
                      int i;
                      int j;<caret>
                      """);
  }

  @Test
  public void testTryWithCatches() {
    assertUnwrapped("""
                      try {
                          int i;<caret>
                      } catch(RuntimeException e) {
                          int j;
                      } catch(Exception e) {
                          int k;
                      }
                      """,

                    "int i;<caret>\n");
  }

  @Test
  public void testTryWithFinally() {
    assertUnwrapped("""
                      try {
                          int i;<caret>
                      } finally {
                          int j;
                      }
                      """,

                    """
                      int i;
                      int j;<caret>
                      """);
  }

  @Test
  public void testFinallyBlock() {
    assertUnwrapped("""
                      try {
                          int i;
                      } finally {
                          int j;<caret>
                      }
                      """,

                    """
                      int i;
                      int j;<caret>
                      """);
  }

  @Test
  public void testFinallyBlockWithCatch() {
    assertUnwrapped("""
                      try {
                          int i;
                      } catch(Exception e) {
                          int j;
                      } finally {
                          int k;<caret>
                      }
                      """,

                    """
                      int i;
                      int k;<caret>
                      """);
  }

  @Test
  public void testCatchBlock() {
    assertUnwrapped("""
                      try {
                          int i;
                      } catch(Exception e) {
                          int j;<caret>
                      }
                      """,

                    "int i;<caret>\n");
  }

  @Test
  public void testManyCatchBlocks() {
    assertUnwrapped("""
                      try {
                          int i;
                      } catch(RuntimeException e) {
                          int j;<caret>
                      } catch(Exception e) {
                          int k;
                      }
                      """,

                    """
                      try {
                          int i;
                      } <caret>catch(Exception e) {
                          int k;
                      }
                      """);
  }

  @Test
  public void testWatchBlockWithFinally() {
    assertUnwrapped("""
                      try {
                          int i;
                      } catch(Exception e) {
                          int j;<caret>
                      } finally {
                          int k;
                      }
                      """,

                    """
                      int i;
                      int k;<caret>
                      """);
  }

  @Test
  public void testTryFinally() {
    assertOptions("""
                    try {
                    } finally {
                        <caret>
                    }
                    """,

                  "Unwrap 'try...'");
  }

  @Test
  public void testTryWithOnlyOneCatch() {
    assertOptions("""
                    try {
                    } catch(Exception e) {
                        <caret>
                    }
                    """,

                  "Unwrap 'try...'");
  }

  @Test
  public void testTryWithSeveralCatches() {
    assertOptions("""
                    try {
                    } catch(Exception e) {
                    } catch(Exception e) {
                        <caret>
                    } catch(Exception e) {
                    }
                    """,

                  "Remove 'catch...'",
                  "Unwrap 'try...'");
  }

  @Test
  public void testTryWithResources() {
    CodeInsightTestFixture fixture = codeInsightFixture.get();
    IdeaTestUtil.setProjectLanguageLevel(fixture.getProject(), LanguageLevel.JDK_1_9, fixture.getTestRootDisposable());
    assertUnwrapped("""
                      AutoCloseable s = null;
                      try (AutoCloseable r = null; s) {
                          <caret>System.out.println();
                      }""",

                    """
                      AutoCloseable s = null;
                      AutoCloseable r = null;
                      System.out.println();
                      """);
  }
}
