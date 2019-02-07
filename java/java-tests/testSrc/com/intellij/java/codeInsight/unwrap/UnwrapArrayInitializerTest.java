// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapArrayInitializerTest extends UnwrapTestCase {
  public void testUnwrap() {
    assertUnwrapped("{\n" +
                    "  int[] arr = new int[]{<caret>1};\n" +
                    "}\n",

                    "{\n" +
                    "  int[] arr = {<caret>1};\n" +
                    "}\n");
  }

  public void testNotAvailable() {
    assertOptions("{\n" +
                  "  int[] arr;\n" +
                  "  arr = new int[]{<caret>1}" +
                  "}\n", "Unwrap braces");
  }
}