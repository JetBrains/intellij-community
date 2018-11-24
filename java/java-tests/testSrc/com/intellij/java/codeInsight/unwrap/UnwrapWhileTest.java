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

public class UnwrapWhileTest extends UnwrapTestCase {
  public void testWhileWithStatement() throws Exception {
    assertUnwrapped("while(true) Sys<caret>tem.gc();\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testWhileWithBlock() throws Exception {
    assertUnwrapped("while(true) {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testDoWhile() throws Exception {
    assertUnwrapped("do {\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "} while(true);\n",

                    "Sys<caret>tem.gc();\n");
  }

  public void testEmptyWhile() throws Exception {
    assertUnwrapped("{\n" +
                    "    while<caret>(true);\n" +
                    "}\n",

                    "{\n" +
                    "<caret>}\n");
  }
}
