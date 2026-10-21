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
public class UnwrapIfTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, 0);
  }

  private void assertUnwrapped(String codeBefore, String expectedCodeAfter, int option) {
    UnwrapTestUtil.assertUnwrapped(codeInsightFixture.get(), codeBefore, expectedCodeAfter, option);
  }

  private void assertOptions(String code, String... expectedOptions) {
    UnwrapTestUtil.assertOptions(codeInsightFixture.get(), code, expectedOptions);
  }

  @Test
  public void testDoesNothingOutsideOfStatements() {
    assertUnwrapped("<caret>int i = 0;\n",

                    "<caret>int i = 0;\n");
  }

  @Test
  public void testIfWithSingleStatement() {
    assertUnwrapped("if(true) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }

  @Test
  public void testIfBlock() {
    assertUnwrapped("""
                      if(true) {
                          int i;<caret>
                      }
                      """,

                    "int i;<caret>\n");
  }

  @Test
  public void testIfBlockWithComment() {
    assertUnwrapped("""
                      if(true) {
                          // a comment
                          int i;<caret>
                      }
                      """,

                    """
                      // a comment
                      int i;<caret>
                      """);
  }

  @Test
  public void testIfMultiStatementBlock() {
    assertUnwrapped("""
                      if(true) {
                          int i;

                          int j;<caret>
                      }
                      """,

                    """
                      int i;

                      int j;<caret>
                      """);
  }

  @Test
  public void testIfEmpty() {
    assertUnwrapped("""
                      {
                          if(true<caret>);
                      }
                      """,

                    """
                      {
                      <caret>}
                      """);
  }

  @Test
  public void testIfEmptyBlock() {
    assertUnwrapped("""
                      {
                          if(true<caret>) {}
                      }
                      """,

                    """
                      {
                      <caret>}
                      """);
  }

  @Test
  public void testIfIncomplete() {
    assertUnwrapped("""
                      {
                          if(true<caret>)
                      }
                      """,

                    """
                      {
                      <caret>}
                      """);
  }

  @Test
  public void testDoesNotAffectNeighbours() {
    assertUnwrapped("""
                      if(true) {}
                      if(false) {<caret>}
                      if(true && false) {}
                      """,

                    """
                      if(true) {}
                      <caret>if(true && false) {}
                      """);
  }

  @Test
  public void testIfWithElse() {
    assertUnwrapped("""
                      if(true) {
                          int i;<caret>
                      } else {
                          int j;
                      }
                      """,

                    "int i;<caret>\n");
  }

  @Test
  public void testIfWithElses() {
    assertUnwrapped("""
                      if(true) {
                          int i;<caret>
                      } else if (true) {
                          int j;
                      } else {
                          int k;
                      }
                      """,

                    "int i;<caret>\n");
  }

  @Test
  public void testIfElseIncomplete() {
    assertUnwrapped("""
                      if(true) {
                          int i;<caret>
                      } else\s
                      """,

                    "int i;<caret>\n");
  }

  @Test
  public void testUnwrapElse() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } else Sys<caret>tem.gc();
                      }
                      """,

                    """
                      {
                          Sys<caret>tem.gc();
                      }
                      """);
  }

  @Test
  public void testUnwrapElseBlock() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } else {
                              int j;<caret>
                          }
                      }
                      """,

                    """
                      {
                          int j;<caret>
                      }
                      """);
  }

  @Test
  public void testUnwrapElseIf() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } else if (false) {
                              int j;<caret>
                          }
                      }
                      """,

                    """
                      {
                          int j;<caret>
                      }
                      """);
  }

  @Test
  public void testUnwrapIfElseIfElse() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } else if (false) {
                              int j;
                          } else {
                              int k;<caret>
                          }
                      }
                      """,

                    """
                      {
                          int k;<caret>
                      }
                      """);
  }

  @Test
  public void testUnwrapElseWhenCaretRightInTheElseKeyword() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } el<caret>se {
                              int j;
                          }
                      }
                      """,

                    """
                      {
                          int j;<caret>
                      }
                      """);
  }

  @Test
  public void testRemovesWhileIfWhenElseIsIsIncomplete() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } el<caret>se\s
                      }
                      """,

                    """
                      {
                          int i;<caret>
                      }
                      """);
  }

  @Test
  public void testRemoveElse() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } else {
                              int j;<caret>
                          }
                      }
                      """,

                    """
                      {
                          if(true) {
                              int i;
                          }<caret>
                      }
                      """,

                    1);
  }

  @Test
  public void testRemoveElseWhenCaretRightInTheElseKeyword() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } el<caret>se {
                              int j;
                          }
                      }
                      """,

                    """
                      {
                          if(true) {
                              int i;
                          }<caret>
                      }
                      """,

                    1);
  }

  @Test
  public void testRemoveElseIf() {
    assertUnwrapped("""
                      {
                          if(true) {
                              int i;
                          } else if(true) {
                              int j;<caret>
                          }
                      }
                      """,

                    """
                      {
                          if(true) {
                              int i;
                          }<caret>
                      }
                      """,

                    1);
  }

  @Test
  public void testRemoveElseIfElse() {
    assertUnwrapped("""
                      if(true) {
                          int i;
                      } else if (true) {
                          int j;<caret>
                      } else {
                          int k;
                      }
                      """,

                    """
                      if(true) {
                          int i;
                      }<caret> else {
                          int k;
                      }
                      """,

                    1);
  }

  @Test
  public void testIfOption() {
    assertOptions("""
                    if (true) {
                        <caret>
                    }
                    """,

                  "Unwrap 'if...'");
  }

  @Test
  public void testIfElseOption() {
    assertOptions("""
                    if (true) {
                    } else {
                        <caret>
                    }
                    """,

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  @Test
  public void testDoesNotIncludeDirectParentIfsWhenElseIsSelected() {
    assertOptions("""
                    if (true) {
                    } else if (true) {
                    } else if (true) {
                    } else {
                        <caret>
                    }
                    """,

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  @Test
  public void testDoesNotIncludeIndirectParentIfsWhenElseIsSelected() {
    assertOptions("""
                    if (true) {
                    } else if (true) {
                    } else {
                        if (true) {
                        } else {
                            <caret>
                        }
                    }
                    """,

                  "Unwrap 'else...'",
                  "Remove 'else...'",
                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  @Test
  public void testIfElseIfOption() {
    assertOptions("""
                    if (true) {
                    } else if (false) {
                        <caret>
                    }
                    """,

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  @Test
  public void testIfInsideElseOption() {
    assertOptions("""
                    if (true) {
                    } else {
                      if (false) {
                        <caret>
                      }
                    }
                    """,

                  "Unwrap 'if...'",
                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }

  @Test
  public void testIfElseOptionWhenCaretIsRughtOnTheElseKeyword() {
    assertOptions("""
                    if (true) {
                    } el<caret>se {
                    }
                    """,

                  "Unwrap 'else...'",
                  "Remove 'else...'");
  }
}