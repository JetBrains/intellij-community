// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class JavaPostfixTemplateInCodeFragmentTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
  }

  private PsiElement getContextElement() {
    PsiClass psiClass = myFixture.addClass("package foo; public class Context { void foo() { int x = 0; } }");
    PsiFile ctxFile = psiClass.getContainingFile();
    PsiElement context = ctxFile.findElementAt(ctxFile.getText().indexOf("int x"));
    assertNotNull(context);
    return context;
  }

  public void testIfPostfixApplicableInCodeFragment() {
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(getProject())
      .createCodeBlockCodeFragment("true.if", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());

    JavaPostfixTemplateProvider provider = new JavaPostfixTemplateProvider();
    assertTrue("'.if' postfix template should be applicable in code fragment",
               PostfixLiveTemplate.isApplicableTemplate(provider, ".if", fragment, myFixture.getEditor()));
  }

  public void testNotNullPostfixApplicableInCodeFragment() {
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(getProject())
      .createCodeBlockCodeFragment("\"hello\".notnull<caret>", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.type("\t");
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    String text = myFixture.getEditor().getDocument().getText();
    assertTrue("Expected 'hello != null' in result, got: " + text,
               text.contains("\"hello\" != null"));
  }

  public void testCastPostfixExpandsInCodeFragment() {
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(getProject())
      .createCodeBlockCodeFragment("\"hello\".cast<caret>", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.type("\t");
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    String text = myFixture.getEditor().getDocument().getText();
    assertTrue("Expected cast expression in result, got: " + text,
               text.contains("(") && text.contains(")") && text.contains("\"hello\""));
  }

  public void testIfPostfixExpandsInCodeFragment() {
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(getProject())
      .createCodeBlockCodeFragment("true.if<caret>", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.type("\t");
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    String text = myFixture.getEditor().getDocument().getText();
    assertTrue("Expected 'if (true)' in result, got: " + text,
               text.contains("if (true)"));
  }

  public void testSoutPostfixExpandsInCodeFragment() {
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(getProject())
      .createCodeBlockCodeFragment("\"hello\".sout<caret>", context, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.type("\t");
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    String text = myFixture.getEditor().getDocument().getText();
    assertTrue("Expected 'System.out.println' in result, got: " + text,
               text.contains("System.out.println"));
  }

  public void testCastPostfixApplicableInExpressionFragment() {
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(getProject())
      .createExpressionCodeFragment("\"hello\".cast", context, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());

    JavaPostfixTemplateProvider provider = new JavaPostfixTemplateProvider();
    assertTrue("'.cast' postfix template should be applicable in expression fragment",
               PostfixLiveTemplate.isApplicableTemplate(provider, ".cast", fragment, myFixture.getEditor()));
  }

  public void testVarPostfixNotApplicableInExpressionFragment() {
    PsiElement context = getContextElement();
    PsiFile fragment = JavaCodeFragmentFactory.getInstance(getProject())
      .createExpressionCodeFragment("\"hello\".var", context, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getTextLength());

    JavaPostfixTemplateProvider provider = new JavaPostfixTemplateProvider();
    assertFalse("'.castvar' postfix template should not be applicable in expression fragment",
                PostfixLiveTemplate.isApplicableTemplate(provider, ".var", fragment, myFixture.getEditor()));
  }
}
