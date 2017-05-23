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