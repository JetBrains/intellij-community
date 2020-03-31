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
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.JavaExpectedHighlightingData;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.testFramework.ExpectedHighlightingData;

public class JavaSymbolHighlightingTest extends LightDaemonAnalyzerTestCase {

  public void testImplicitAnonymousClassParameterHighlighting_InsideLambda() {
    configureFromFileText("Test.java",
                          "class T {" +
                          "    private T(int i){}\n" +
                          "    public void test() {\n" +
                          "        int xxx = 12;\n" +
                          "        Runnable r = () -> {\n" +
                          "            check(<symbolName type=\"IMPLICIT_ANONYMOUS_CLASS_PARAMETER\">xxx</symbolName>);\n" +
                          "            new T(<symbolName type=\"IMPLICIT_ANONYMOUS_CLASS_PARAMETER\">xxx</symbolName>){};" +
                          "        };" +
                          "    }\n" +
                          "    public void check(int a) {}\n" +
                          "}");
    
    doTestConfiguredFile(true, true, true, null);
  }

  public void testReassignedVariables() {
    configureFromFileText("Test.java",
                          "class Test {\n" +
                          "  void foo() {\n" +
                          "    int <symbolName type=\"REASSIGNED_LOCAL_VARIABLE\">x</symbolName> = 0;\n" +
                          "    x = 1;\n" +
                          "  }\n" +
                          "  \n" +
                          "  String loop() {\n" +
                          "    String <symbolName type=\"REASSIGNED_LOCAL_VARIABLE\">a</symbolName>;\n" +
                          "\n" +
                          "    do {\n" +
                          "      a = \"aaaa\";\n" +
                          "    }\n" +
                          "    while (a.equals(\"bbb\"));\n" +
                          "    return a;\n" +
                          "  }\n" +
                          "}");
    doTestConfiguredFile(true, true, true, null);
  }
  
  @Override
  protected ExpectedHighlightingData getExpectedHighlightingData(boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos) {
    JavaExpectedHighlightingData data = new JavaExpectedHighlightingData(getEditor().getDocument(), false, false, false, true, getFile());
    data.checkSymbolNames();
    return data;
  }
}
