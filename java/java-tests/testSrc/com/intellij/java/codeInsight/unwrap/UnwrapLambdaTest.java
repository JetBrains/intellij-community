// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapLambdaTest extends UnwrapTestCase {
  public void testUnwrap() {
    assertUnwrapped("{\n" +
                    "    Runnable r = () -> {\n" +
                    "       Sys<caret>tem.gc();\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testUnwrapNestedLambda() {
    assertUnwrapped("{\n" +
                    "    bar(() -> bar(() -> Sys<caret>tem.gc()));\n" +
                    "}\n",

                    "{\n" +
                    "    bar(() -> Sys<caret>tem.gc());\n" +
                    "}\n", 1);
  }
  
  public void testUnwrapExpressionDeclaration() {
    assertUnwrapped("{\n" +
                    "    interface I {int get();}" +
                    "    I i = () -> <caret>1;\n" +
                    "}\n",

                    "{\n" +
                    "    interface I {int get();}" +
                    "    I i = 1;\n" +
                    "}\n");
  }

  public void testUnwrapBlockDeclaration() {
    assertUnwrapped("{\n" +
                    "    interface I {int get();}" +
                    "    I i = () -> { return <caret>1;};\n" +
                    "}\n",

                    "{\n" +
                    "    interface I {int get();}" +
                    "    I i = 1;\n" +
                    "}\n");
  }

  public void testUnwrapAssignment() {
    assertUnwrapped("{\n" +
                    "    interface I {int get();}" +
                    "    void bar(I i) {}" +
                    "    I i = bar(() -> 1<caret>1);\n" +
                    "}\n",

                    "{\n" +
                    "    interface I {int get();}" +
                    "    void bar(I i) {}" +
                    "    I i = 11;\n" +
                    "}\n", 1);
  }

  public void testUnwrapAssignmentWithCodeBlock() {
    assertUnwrapped("{\n" +
                    "    Runnable r = () -> {in<caret>t i = 0;};\n" +
                    "}\n",

                    "{\n" +
                    "    int i = 0;\n" +
                    "}\n");
  }

  public void testUnwrapUnresolved() {
    assertUnwrapped("{\n" +
                    "    () -> <caret>null;\n" +
                    "}\n",

                    "{\n" +
                    "    null;\n" +
                    "}\n");
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