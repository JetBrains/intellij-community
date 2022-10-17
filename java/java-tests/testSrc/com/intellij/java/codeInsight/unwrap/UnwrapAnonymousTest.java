// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapAnonymousTest extends UnwrapTestCase {
  public void testUnwrap() {
    assertUnwrapped("""
                      {
                          new Runnable() {
                              public void run() {
                                  Sys<caret>tem.gc();
                              }
                          }
                      }
                      """,

                    """
                      {
                          Sys<caret>tem.gc();
                      }
                      """);
  }
  
  public void testUnwrapDeclaration() {
    assertUnwrapped("""
                      {
                          Runnable r = new Runnable() {
                              public void run() {
                                  Sys<caret>tem.gc();
                              }
                          }
                      }
                      """,

                    """
                      {
                          Sys<caret>tem.gc();
                      }
                      """);
  }

  public void testUnwrapAssignment() {
    assertUnwrapped("""
                      {
                          Runnable r = null;
                          r = new Runnable() {
                              public void run() {
                                  Sys<caret>tem.gc();
                              }
                          }
                      }
                      """,

                    """
                      {
                          Runnable r = null;
                          Sys<caret>tem.gc();
                      }
                      """);
  }

  public void testInsideMethodCall() {
    assertUnwrapped("""
                      {
                          foo(new Runnable() {
                              public void run() {
                                  Sys<caret>tem.gc();
                              }
                          });
                      }
                      """,

                    """
                      {
                          Sys<caret>tem.gc();
                      }
                      """);
  }

  public void testInsideAnotherAnonymous() {
    assertUnwrapped("""
                      {
                          new Runnable() {
                              public void run() {
                                  int i = 0;
                                  new Runnable() {
                                      public void run() {
                                          Sys<caret>tem.gc();
                                      }
                                  };
                              }
                          };
                      }
                      """,

                    """
                      {
                          new Runnable() {
                              public void run() {
                                  int i = 0;
                                  Sys<caret>tem.gc();
                              }
                          };
                      }
                      """);
  }

  public void testInsideAnotherAnonymousWithAssignment() {
    assertUnwrapped("""
                      {
                          Runnable r = new Runnable() {
                              public void run() {
                                  int i = 0;
                                  new Runnable() {
                                      public void run() {
                                          Sys<caret>tem.gc();
                                      }
                                  };
                              }
                          };
                      }
                      """,

                    """
                      {
                          Runnable r = new Runnable() {
                              public void run() {
                                  int i = 0;
                                  Sys<caret>tem.gc();
                              }
                          };
                      }
                      """);
  }

  public void testDeclarationWithMethodCall() {
    assertUnwrapped("""
                      {
                          Object obj = foo(new Runnable() {
                              public void run() {
                                  Sys<caret>tem.gc();
                              }
                          });
                      }
                      """,

                    """
                      {
                          Sys<caret>tem.gc();
                      }
                      """);
  }

  public void testSeveralMethodCalls() {
    assertUnwrapped("""
                      {
                          bar(foo(new Runnable() {
                              public void run() {
                                  Sys<caret>tem.gc();
                              }
                          }));
                      }
                      """,

                    """
                      {
                          Sys<caret>tem.gc();
                      }
                      """);
  }

  public void testWhenCaretIsOnDeclaration() {
    assertUnwrapped("""
                      {
                          Runnable r = new Run<caret>nable() {
                              public void run() {
                                  System.gc();
                              }
                          }
                      }
                      """,

                    """
                      {
                          System.gc();<caret>
                      }
                      """);
  }
  
  public void testEmptyClass() {
    assertUnwrapped("""
                      {
                          Runnable r = new Run<caret>nable() {}
                      }
                      """,

                    """
                      {
                      <caret>}
                      """);
  }

  public void testDoNothingWithSeveralMethods() {
    assertUnwrapped("""
                      Runnable r = new Runnable() {
                          public void one() {
                              // method one
                              System.gc();
                          }
                          public void two() {
                              // method two
                              Sys<caret>tem.gc();
                          }
                      }
                      """,

                    """
                      Runnable r = new Runnable() {
                          public void one() {
                              // method one
                              System.gc();
                          }
                          public void two() {
                              // method two
                              Sys<caret>tem.gc();
                          }
                      }
                      """);
  }

  public void testReassignValue() {
    assertUnwrapped("""
                      int i = new Comparable<String>() {
                                  public int compareTo(String o) {
                                      return <caret>0;
                                  }
                              };
                      """,

                    "int i = 0;\n");
  }

  public void testReturnValue() {
    assertUnwrapped("""
                      return new Comparable<Integer>() {
                          public int compareTo(Integer o) {
                              return <caret>0;
                          }
                      };
                      """
                    ,

                    "return 0;\n");
  }
}