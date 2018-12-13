// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template;


import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;


public class LiveTemplateAutoPopupTest extends CompletionAutoPopupTestCase {
  @Override
  protected void setUp() {
    super.setUp();
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
  }

  public void testNoPopupIfOnlyTemplatesInCompletionList() {
    myFixture.configureByText("a.java", "<caret>");
    createTemplate("z");
    type("z");
    assertNull(myFixture.getLookup());
  }

  public void testPopupIfNotOnlyTemplatesInCompletionList() {
    myFixture.configureByText("a.java", "<caret>");
    createTemplate("c");
    type("c");
    assertNotNull(myFixture.getLookup());
    myFixture.assertPreferredCompletionItems(0, "c", "class");
  }

  public void testPopupOnExplicitCompletionIfOnlyTemplatesInList() {
    myFixture.configureByText("a.java", "z<caret>");
    createTemplate("z");
    myFixture.completeBasic();
    assertNotNull(myFixture.getLookup());
    myFixture.assertPreferredCompletionItems(0, "z");
  }

  private void createTemplate(@NotNull String templateName) {
    TemplateManager manager = TemplateManager.getInstance(getProject());
    Template template = manager.createTemplate(templateName, "user", "");
    TemplateContextType contextType = ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), JavaCodeContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(contextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
  }
}