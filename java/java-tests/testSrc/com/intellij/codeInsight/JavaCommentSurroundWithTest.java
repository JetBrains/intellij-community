// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.template.JavaCommentContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;

public class JavaCommentSurroundWithTest extends LightJavaCodeInsightTestCase {
  private static final String TEMPLATE_KEY = "surroundWithInlineCodeForTest";

  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testJavadocBeforeInlineTag() {
    doTest();
  }

  public void testJavadocAfterInlineTag() {
    doTest();
  }

  public void testJavadocBeforeInlineTagMarkdown() {
    doTest();
  }

  public void testJavadocAfterInlineTagMarkdown() {
    doTest();
  }

  private void doTest() {
    registerInlineCodeTemplate();
    String baseName = "/codeInsight/surroundWith/" + getTestName(false);
    configureByFile(baseName + ".java");
    invokeInlineCodeTemplate();
    checkResultByFile(baseName + "_after.java");
  }

  private void registerInlineCodeTemplate() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    TemplateImpl template = (TemplateImpl)TemplateManager.getInstance(getProject())
      .createTemplate(TEMPLATE_KEY, "user", "{@code $SELECTION$}");
    template.getTemplateContext().setEnabled(TemplateContextTypes.getByClass(JavaCommentContextType.class), true);
    CodeInsightTestUtil.addTemplate(template, getTestRootDisposable());
  }

  private void invokeInlineCodeTemplate() {
    List<AnAction> group = SurroundWithTemplateHandler.createActionGroup(getEditor(), getFile(), new HashSet<>());
    InvokeTemplateAction action = (InvokeTemplateAction)ContainerUtil.find(
      group, a -> a.getTemplatePresentation().getText().contains(TEMPLATE_KEY));
    assertNotNull("Inline code surround-with template not offered for the current selection", action);
    action.perform();
  }
}
