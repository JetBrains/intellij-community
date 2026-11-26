// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.JavaExpectedHighlightingData;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.ReassignedVariableInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class JavaSymbolHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, () -> List.of(new ReassignedVariableInspection()));

  @Test
  public void testImplicitAnonymousClassParameterHighlighting_InsideLambda() {
    daemonFixture.get().configureFromFileText("Test.java",
                          """
                            class T {    private T(int i){}
                                public void test() {
                                    int xxx = 12;
                                    Runnable r = () -> {
                                        check(<symbolName type="IMPLICIT_ANONYMOUS_CLASS_PARAMETER">xxx</symbolName>);
                                        new T(<symbolName type="IMPLICIT_ANONYMOUS_CLASS_PARAMETER">xxx</symbolName>){};        };    }
                                public void check(int a) {}
                            }""");
    
    checkSymbolHighlighting();
  }

  @Test
  public void testReassignedVariables() {
    daemonFixture.get().configureFromFileText("Test.java",
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
    checkSymbolHighlighting();
  }
  
  private void checkSymbolHighlighting() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    JavaExpectedHighlightingData data = ReadAction.computeBlocking(
      () -> new JavaExpectedHighlightingData(daemon.getFixture().getEditor().getDocument(), true, true, true, true));
    data.checkSymbolNames();
    daemon.checkHighlighting(data, null);
  }
}
