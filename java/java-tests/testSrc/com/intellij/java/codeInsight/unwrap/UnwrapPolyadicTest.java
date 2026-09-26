// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapPolyadicTest extends UnwrapTestCase {
  public void testUnwrapInVariable() {
    assertUnwrapped("""
                      {
                      String s = "a" + "<caret>b" + "c";
                      }
                      """,

                    """
                      {
                      String s = "<caret>b";
                      }
                      """);
  }

  public void testUnwrapCall() {
    assertUnwrapped("""
                      {
                       foo("a" + "<caret>b" + "c");
                      }
                      """,

                    """
                      {
                       foo("<caret>b");
                      }
                      """);
  }
  
}