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
                          """
                            class T {    private T(int i){}
                                public void test() {
                                    int xxx = 12;
                                    Runnable r = () -> {
                                        check(<symbolName type="IMPLICIT_ANONYMOUS_CLASS_PARAMETER">xxx</symbolName>);
                                        new T(<symbolName type="IMPLICIT_ANONYMOUS_CLASS_PARAMETER">xxx</symbolName>){};        };    }
                                public void check(int a) {}
                            }""");
    
    doTestConfiguredFile(true, true, true, null);
  }

  public void testReassignedVariables() {
    configureFromFileText("Test.java",
                          """
                            class Test {
                              void foo() {
                                @SuppressWarnings("ReassignedVariable") int y = 0;
                                y = 7;     int x = 0;
                                <text_attr descr="Reassigned local variable">x</text_attr> = 1;
                              }
                             \s
                              String loop() {
                                String <text_attr descr="Reassigned local variable">a</text_attr>;

                                do {
                                  <text_attr descr="Reassigned local variable">a</text_attr> = "aaaa";
                                }
                                while (<text_attr descr="Reassigned local variable">a</text_attr>.equals("bbb"));
                                return <text_attr descr="Reassigned local variable">a</text_attr>;
                              }
                            }""");
    doTestConfiguredFile(true, true, true, null);
  }
  
  @Override
  protected ExpectedHighlightingData getExpectedHighlightingData(boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos) {
    JavaExpectedHighlightingData data = new JavaExpectedHighlightingData(getEditor().getDocument(), checkWarnings, checkWeakWarnings, checkInfos, true);
    data.checkSymbolNames();
    return data;
  }
}
