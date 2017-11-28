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

public class UnwrapLambdaTest extends UnwrapTestCase {
  public void testUnwrap() throws Exception {
    assertUnwrapped("{\n" +
                    "    Runnable r = () -> {\n" +
                    "       Sys<caret>tem.gc();\n" +
                    "    }\n" +
                    "}\n",

                    "{\n" +
                    "    Sys<caret>tem.gc();\n" +
                    "}\n");
  }

  public void testUnwrapNestedLambda() throws Exception {
    assertUnwrapped("{\n" +
                    "    bar(() -> bar(() -> Sys<caret>tem.gc()));\n" +
                    "}\n",

                    "{\n" +
                    "    bar(() -> Sys<caret>tem.gc());\n" +
                    "}\n", 1);
  }
  
  public void testUnwrapExpressionDeclaration() throws Exception {
    assertUnwrapped("{\n" +
                    "    interface I {int get();}" +
                    "    I i = () -> <caret>1;\n" +
                    "}\n",

                    "{\n" +
                    "    interface I {int get();}" +
                    "    I i = 1;\n" +
                    "}\n");
  }

  public void testUnwrapBlockDeclaration() throws Exception {
    assertUnwrapped("{\n" +
                    "    interface I {int get();}" +
                    "    I i = () -> { return <caret>1;};\n" +
                    "}\n",

                    "{\n" +
                    "    interface I {int get();}" +
                    "    I i = 1;\n" +
                    "}\n");
  }

  public void testUnwrapAssignment() throws Exception {
    assertUnwrapped("{\n" +
                    "    interface I {int get();}" +
                    "    void bar(I i) {}" +
                    "    I i = bar(() -> 1<caret>1);\n" +
                    "}\n",

                    "{\n" +
                    "    interface I {int get();}" +
                    "    void bar(I i) {}" +
                    "    I i = 11;\n" +
                    "}\n", 1);
  }

  public void testUnwrapUnresolved() throws Exception {
    assertUnwrapped("{\n" +
                    "    () -> <caret>null;\n" +
                    "}\n",

                    "{\n" +
                    "    null;\n" +
                    "}\n");
  }

  @Override
  protected String createCode(String codeBefore) {
    return "public class A {\n" +
           "    void foo() {\n" +
           indentTwice(codeBefore) +
           "    }\n" +
           "    void bar(Runnable r){}\n" +
           "}";
  }
}