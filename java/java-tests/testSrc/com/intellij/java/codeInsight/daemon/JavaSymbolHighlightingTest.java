// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.JavaExpectedHighlightingData;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ReassignedVariableInspection;
import com.intellij.testFramework.ExpectedHighlightingData;
import org.jetbrains.annotations.NotNull;

public class JavaSymbolHighlightingTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new ReassignedVariableInspection()};
  }

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
                          "    @SuppressWarnings(\"ReassignedVariable\") int y = 0;\n" +
                          "    y = 7; " +
                          "    int x = 0;\n" +
                          "    <text_attr descr=\"Reassigned local variable\">x</text_attr> = 1;\n" +
                          "  }\n" +
                          "  \n" +
                          "  String loop() {\n" +
                          "    String <text_attr descr=\"Reassigned local variable\">a</text_attr>;\n" +
                          "\n" +
                          "    do {\n" +
                          "      <text_attr descr=\"Reassigned local variable\">a</text_attr> = \"aaaa\";\n" +
                          "    }\n" +
                          "    while (<text_attr descr=\"Reassigned local variable\">a</text_attr>.equals(\"bbb\"));\n" +
                          "    return <text_attr descr=\"Reassigned local variable\">a</text_attr>;\n" +
                          "  }\n" +
                          "}");
    doTestConfiguredFile(true, true, true, null);
  }
  
  @Override
  protected ExpectedHighlightingData getExpectedHighlightingData(boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos) {
    JavaExpectedHighlightingData data = new JavaExpectedHighlightingData(getEditor().getDocument(), checkWarnings, checkWeakWarnings, checkInfos, true);
    data.checkSymbolNames();
    return data;
  }
}
