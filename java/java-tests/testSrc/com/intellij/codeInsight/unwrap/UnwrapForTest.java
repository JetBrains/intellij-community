package com.intellij.codeInsight.unwrap;

public class UnwrapForTest extends UnwrapTestCase {
  public void testForWithStatement() throws Exception {
    assertUnwrapped("for(int i = 0; i < 10; i++) Sys<caret>tem.gc();\n",

                    "int i = 0;\n" +
                    "Sys<caret>tem.gc();\n");
  }

  public void testForWithBlock() throws Exception {
    assertUnwrapped("for(int i = 0; i < 10; i++) {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n",

                    "int i = 0;\n" +
                    "Sys<caret>tem.gc();\n");
  }

  public void testEmptyFor() throws Exception {
    assertUnwrapped("for<caret>(int i = 0; i < 10; i++);\n",

                    "int i = 0;<caret>\n");
  }

  public void testForEachWithStatement() throws Exception {
    assertUnwrapped("for(String s : strings) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }
  
  public void testForEachWithBlock() throws Exception {
    assertUnwrapped("for(String s : strings) {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n",

                    "Sys<caret>tem.gc();\n");
  }
}