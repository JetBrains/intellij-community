/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.UnwrapTestCase;

public class UnwrapBracesTest extends UnwrapTestCase {
  public void testBraces() throws Exception {
    assertUnwrapped("{\n" +
                    "    int i;<caret>\n" +
                    "}\n",

                    "int i;<caret>\n");
  }

  public void testEmptyBraces() throws Exception {
    assertUnwrapped("{\n" +
                    "    {<caret>}\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }

  public void testBracesWithComments() throws Exception {
    assertUnwrapped("{\n" +
                    "    // a <caret>comment\n" +
                    "    int i = 0;\n" +
                    "}\n",

                    "// a <caret>comment\n" +
                    "int i = 0;\n");
  }

  public void testTrimmingTheLeadingAndTrailingWhileSpaces() throws Exception {
    assertUnwrapped("{\n" +
                    "    \n" +
                    "    \n" +
                    "    int i<caret> = 0;\n" +
                    "    \n" +
                    "    \n" +
                    "}\n",

                    "int i<caret> = 0;\n");
  }

  public void testBracesOptions() throws Exception {
    assertOptions("{\n" +
                  "    {\n" +
                  "        int i;<caret>\n" +
                  "    }\n" +
                  "}\n",

                  "Unwrap braces",
                  "Unwrap braces");
  }
}
