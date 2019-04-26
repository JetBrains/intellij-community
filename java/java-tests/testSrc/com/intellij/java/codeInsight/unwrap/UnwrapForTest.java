// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapForTest extends UnwrapTestCase {
  public void testForWithStatement() {
    assertUnwrapped("for(int i = 0; i < 10; i++) Sys<caret>tem.gc();\n",

                    "int i = 0;\n" +
                    "Sys<caret>tem.gc();\n");
  }

  public void testForWithBlock() {
    assertUnwrapped("for(int i = 0; i < 10; i++) {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n",

                    "int i = 0;\n" +
                    "Sys<caret>tem.gc();\n");
  }

  public void testEmptyFor() {
    assertUnwrapped("for<caret>(int i = 0; i < 10; i++);\n",

                    "int i = 0;<caret>\n");
  }

  public void testForEachWithStatement() {
    assertUnwrapped("for(String s : strings) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }
  
  public void testForEachWithBlock() {
    assertUnwrapped("for(String s : strings) {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n",

                    "Sys<caret>tem.gc();\n");
  }
}