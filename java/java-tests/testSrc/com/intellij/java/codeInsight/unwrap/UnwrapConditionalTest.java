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

public class UnwrapConditionalTest extends UnwrapTestCase {
  public void testThat() throws Exception {
    assertUnwrapped("xxx(f ? <caret>'1' : '2');\n",
                    "xxx('1');\n");
  }

  public void testElse() throws Exception {
    assertUnwrapped("xxx(f ? '1' : '2' +<caret> 3);\n",
                    "xxx('2' +<caret> 3);\n");
  }

  public void testFromParameterList2() throws Exception {
    assertUnwrapped("xxx(11, f ? '1' : '2' +<caret> 3, 12);\n",
                    "xxx(11, '2' +<caret> 3, 12);\n");
  }

  public void testCond1() throws Exception {
    assertUnwrapped("f <caret>? \"1\" : \"2\" + 3;\n",
                    "\"1\";\n");
  }

  public void testCond2() throws Exception {
    assertUnwrapped("<caret>f ? \"1\" : \"2\" + 3;\n",
                    "\"1\";\n");
  }

  public void testUnwrapUnderAssignmentExpression() throws Exception {
    assertUnwrapped("String s = f ? \"1<caret>\" : \"2\";\n",
                    "String s = \"1\";\n");
  }
}