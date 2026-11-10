// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaPostfixTemplateInCodeFragmentTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
  }

  private PsiElement getContextElement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = myFixture.addClass("package foo; public class Context { void foo() { int x = 0; } }");
    PsiFile ctxFile = psiClass.getContainingFile();
    PsiElement context = ReadAction.computeBlocking(() -> ctxFile.findElementAt(ctxFile.getText().indexOf("int x")));
    assertNotNull(context);
    return context;
  }

  @Test
  public void testIfPostfixApplicableInCodeFragment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
      .createCodeBlockCodeFragment("true.if", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());

      JavaPostfixTemplateProvider provider = new JavaPostfixTemplateProvider();
      assertTrue(PostfixLiveTemplate.isApplicableTemplate(provider, ".if", fragment, myFixture.getEditor()),
                 "'.if' postfix template should be applicable in code fragment");
    });
  }

  @Test
  public void testNotNullPostfixApplicableInCodeFragment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
      .createCodeBlockCodeFragment("\"hello\".notnull<caret>", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      String text = myFixture.getEditor().getDocument().getText();
      assertTrue(text.contains("\"hello\" != null"),
                 "Expected 'hello != null' in result, got: " + text);
    });
  }

  @Test
  public void testCastPostfixExpandsInCodeFragment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
      .createCodeBlockCodeFragment("\"hello\".cast<caret>", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      String text = myFixture.getEditor().getDocument().getText();
      assertTrue(text.contains("(") && text.contains(")") && text.contains("\"hello\""),
                 "Expected cast expression in result, got: " + text);
    });
  }

  @Test
  public void testIfPostfixExpandsInCodeFragment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
      .createCodeBlockCodeFragment("true.if<caret>", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      String text = myFixture.getEditor().getDocument().getText();
      assertTrue(text.contains("if (true)"),
                 "Expected 'if (true)' in result, got: " + text);
    });
  }

  @Test
  public void testSoutPostfixExpandsInCodeFragment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
      .createCodeBlockCodeFragment("\"hello\".sout<caret>", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.type("\t");
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      String text = myFixture.getEditor().getDocument().getText();
      assertTrue(text.contains("System.out.println"),
                 "Expected 'System.out.println' in result, got: " + text);
    });
  }

  @Test
  public void testCastPostfixApplicableInExpressionFragment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
      .createExpressionCodeFragment("\"hello\".cast", context, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());

      JavaPostfixTemplateProvider provider = new JavaPostfixTemplateProvider();
      assertTrue(PostfixLiveTemplate.isApplicableTemplate(provider, ".cast", fragment, myFixture.getEditor()),
                 "'.cast' postfix template should be applicable in expression fragment");
    });
  }

  @Test
  public void testVarPostfixNotApplicableInExpressionFragment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(myFixture.getProject())
      .createExpressionCodeFragment("\"hello\".var", context, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());

      JavaPostfixTemplateProvider provider = new JavaPostfixTemplateProvider();
      assertFalse(PostfixLiveTemplate.isApplicableTemplate(provider, ".var", fragment, myFixture.getEditor()),
                  "'.castvar' postfix template should not be applicable in expression fragment");
    });
  }
}
