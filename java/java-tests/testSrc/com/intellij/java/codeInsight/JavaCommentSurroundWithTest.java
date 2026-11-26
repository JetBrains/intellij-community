// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.template.JavaCommentContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaCommentSurroundWithTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final String TEMPLATE_KEY = "surroundWithInlineCodeForTest";

  @Test
  public void testJavadocBeforeInlineTag() {
    doTest();
  }

  @Test
  public void testJavadocAfterInlineTag() {
    doTest();
  }

  @Test
  public void testJavadocBeforeInlineTagMarkdown() {
    doTest();
  }

  @Test
  public void testJavadocAfterInlineTagMarkdown() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    registerInlineCodeTemplate();
    String baseName = "/codeInsight/surroundWith/" + myFixture.getTestName(false);
    myFixture.configureByFile(baseName + ".java");
    myFixture.runInCommand(() -> {
      invokeInlineCodeTemplate();
      myFixture.checkResultByFile(baseName + "_after.java");
    });
  }

  private void registerInlineCodeTemplate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    TemplateImpl template = (TemplateImpl)TemplateManager.getInstance(myFixture.getProject())
      .createTemplate(TEMPLATE_KEY, "user", "{@code $SELECTION$}");
    template.getTemplateContext().setEnabled(TemplateContextTypes.getByClass(JavaCommentContextType.class), true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
  }

  private void invokeInlineCodeTemplate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<AnAction> group = SurroundWithTemplateHandler.createActionGroup(myFixture.getEditor(), myFixture.getFile(), new HashSet<>());
    InvokeTemplateAction action = (InvokeTemplateAction)ContainerUtil.find(
      group, a -> a.getTemplatePresentation().getText().contains(TEMPLATE_KEY));
    assertNotNull(action, "Inline code surround-with template not offered for the current selection");
    action.perform();
  }
}
