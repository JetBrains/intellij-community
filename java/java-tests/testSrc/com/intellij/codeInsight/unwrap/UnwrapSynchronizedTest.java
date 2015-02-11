package com.intellij.codeInsight.unwrap;

public class UnwrapSynchronizedTest extends UnwrapTestCase {
  public void testUnwrap() throws Exception {
    assertUnwrapped("synchronized(foo) {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n",

                    "Sys<caret>tem.gc();\n");
  }
}