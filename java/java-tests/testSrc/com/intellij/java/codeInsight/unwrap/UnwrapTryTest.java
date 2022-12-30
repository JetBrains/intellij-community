// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

public class UnwrapTryTest extends UnwrapTestCase {
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

  public void testTryFinally() {
    assertOptions("""
                    try {
                    } finally {
                        <caret>
                    }
                    """,

                  "Unwrap 'try...'");
  }

  public void testTryWithOnlyOneCatch() {
    assertOptions("""
                    try {
                    } catch(Exception e) {
                        <caret>
                    }
                    """,

                  "Unwrap 'try...'");
  }

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

  public void testTryWithResources() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_9);
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
