// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class TemplateBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testRunInlineTemplate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class A {
        public String tes<caret>t() {
        }
      }""");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> TemplateBuilderFactory
      .getInstance().createTemplateBuilder(myFixture.getElementAtCaret()).run(myFixture.getEditor(), true));
    myFixture.checkResult("""
                            class A {
                              <caret>public String test() {
                              }
                            }""");
  }
}
