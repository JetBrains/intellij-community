// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapBracesTest extends UnwrapTestCase {
  public void testBraces() {
    assertUnwrapped("""
                      {
                          int i;<caret>
                      }
                      """,

                    "int i;<caret>\n");
  }

  public void testEmptyBraces() {
    assertUnwrapped("""
                      {
                          {<caret>}
                      }
                      """,

                    """
                      {
                      <caret>}
                      """);
  }

  public void testBracesWithComments() {
    assertUnwrapped("""
                      {
                          // a <caret>comment
                          int i = 0;
                      }
                      """,

                    """
                      // a <caret>comment
                      int i = 0;
                      """);
  }

  public void testTrimmingTheLeadingAndTrailingWhileSpaces() {
    assertUnwrapped("""
                      {
                         \s
                         \s
                          int i<caret> = 0;
                         \s
                         \s
                      }
                      """,

                    "int i<caret> = 0;\n");
  }

  public void testBracesOptions() {
    assertOptions("""
                    {
                        {
                            int i;<caret>
                        }
                    }
                    """,

                  "Unwrap braces",
                  "Unwrap braces");
  }
}
