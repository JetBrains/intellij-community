// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapWhileTest extends UnwrapTestCase {
  public void testWhileWithStatement() {
    assertUnwrapped("while(true) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testWhileWithBlock() {
    assertUnwrapped("while(true) {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testDoWhile() {
    assertUnwrapped("do {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "} while(true);\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testEmptyWhile() {
    assertUnwrapped("{\n" +
                    "    while<caret>(true);\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }
}
