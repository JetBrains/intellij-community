// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapArrayInitializerTest extends UnwrapTestCase {
  public void testUnwrap() {
    assertUnwrapped("""
                      {
                        int[] arr = new int[]{<caret>1};
                      }
                      """,

                    """
                      {
                        int[] arr = {<caret>1};
                      }
                      """);
  }

  public void testNotAvailable() {
    assertOptions("""
                    {
                      int[] arr;
                      arr = new int[]{<caret>1}}
                    """, "Unwrap braces");
  }
}