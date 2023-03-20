// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapIfTest extends UnwrapTestCase {
  public void testDoesNothingOutsideOfStatements() {
    assertUnwrapped("<caret>int i = 0;\n",

                    "<caret>int i = 0;\n");
  }

  public void testIfWithSingleStatement() {
    assertUnwrapped("if(true) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testIfBlock() {
    assertUnwrapped("""
                      if(true) {
                          int i;<caret>
                      }
                      """,

                    "int i;<caret>\n");
  }

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

  public void testIfElseIncomplete() {
    assertUnwrapped("""
                      if(true) {
                          int i;<caret>
                      } else\s
                      """,

                    "int i;<caret>\n");
  }

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

  public void testIfOption() {
    assertOptions("""
                    if (true) {
                        <caret>
                    }
                    """,

                  "Unwrap 'if...'");
  }

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