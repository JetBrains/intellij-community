// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapBracesTest extends UnwrapTestCase {
  public void testBraces() {
    assertUnwrapped("{\n" +
                    "    int i;<caret>\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testEmptyBraces() {
    assertUnwrapped("{\n" +
                    "    {<caret>}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testBracesWithComments() {
    assertUnwrapped("{\n" +
                    "    // a <caret>comment\n" +
                    "    int i = 0;\n" +
                    "}\n",

                    "// a <caret>comment\n" +
                    "int i = 0;\n");
  }

  public void testTrimmingTheLeadingAndTrailingWhileSpaces() {
    assertUnwrapped("{\n" +
                    "    \n" +
                    "    \n" +
                    "    int i<caret> = 0;\n" +
                    "    \n" +
                    "    \n" +
                    "}\n",

                    "int i<caret> = 0;\n");
  }

  public void testBracesOptions() {
    assertOptions("{\n" +
                  "    {\n" +
                  "        int i;<caret>\n" +
                  "    }\n" +
                  "}\n",

                  "Unwrap braces",
                  "Unwrap braces");
  }
}
