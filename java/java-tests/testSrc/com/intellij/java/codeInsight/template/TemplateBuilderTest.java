// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class TemplateBuilderTest extends LightJavaCodeInsightFixtureTestCase {
  public void testRunInlineTemplate() {
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
