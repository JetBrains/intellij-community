package com.intellij.codeInsight.unwrap;

public class UnwrapWhileTest extends UnwrapTestCase {
  public void testWhileWithStatement() throws Exception {
    assertUnwrapped("while(true) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testWhileWithBlock() throws Exception {
    assertUnwrapped("while(true) {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testDoWhile() throws Exception {
    assertUnwrapped("do {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "} while(true);\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testEmptyWhile() throws Exception {
    assertUnwrapped("{\n" +
                    "    while<caret>(true);\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }
}
