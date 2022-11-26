// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapLambdaTest extends UnwrapTestCase {
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

  @Override
  protected String createCode(String codeBefore) {
    return "public class A {\n" +
           "    void foo() {\n" +
           indentTwice(codeBefore) +
           "    }\n" +
           "    void bar(Runnable r){}\n" +
           "}";
  }
}