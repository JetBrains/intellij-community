package com.intellij.codeInsight.unwrap;

public class UnwrapArrayInitializerTest extends UnwrapTestCase {
  public void testUnwrap() throws Exception {
    assertUnwrapped("{\n" +
                    "  int[] arr = new int[]{<caret>1};\n" +
                    "}\n",

                    "{\n" +
                    "  int[] arr = {<caret>1};\n" +
                    "}\n");
  }

  public void testNotAvailable() throws Exception {
    assertOptions("{\n" +
                  "  int[] arr;\n" +
                  "  arr = new int[]{<caret>1}" +
                  "}\n", "Unwrap braces");
  }
}