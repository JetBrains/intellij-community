// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineConstantFieldHandler;
import com.intellij.refactoring.inline.InlineConstantFieldProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InlineConstantFieldTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // has to have src.zip
  }

  public void testQualifiedExpression() {
    doTest();
  }

  public void testQualifiedExpressionInLib() {
    doTest(true);
  }

  public void testLocalClassDecoding() {
    doTest();
  }

  public void testQualifiedConstantExpression() {
    doTest();
  }

   public void testQualifiedConstantExpressionReplacedWithAnotherOne() {
    doTest();
  }
  
  public void testStaticallyImportedQualifiedExpression() {
    doTest();
  }

  public void testCastWhenLambdaAsQualifier() {
    doTest();
  }

  public void testConstantFromLibrary() {
    doTest();
  }

  public void testQualifierJava6() {
    doTest();
  }

  public void testFinalInitializedInConstructor() {
    doTest();
  }

  public void testDiamondInitializer() {
    doTest();
  }

  public void testFieldUsedReflectively() {
    doTestConflict("Inlined field is used reflectively");
  }

  public void testFieldInitializedWithParameter() {
    doTestConflict("Field initializer refers to parameter <b><code>a</code></b>, which is not accessible in method <b><code>Test.test()</code></b>");
  }

  public void testFieldInitializedLocalClass() {
    doTestConflict("Field initializer refers to local class <b><code>Local</code></b>, which is not accessible in method <b><code>Test.test()</code></b>");
  }

  public void testFieldInitializedWithParameter1() {
    doTest();
  }

  public void testFieldInitializedWithConstant() {
    doTest();
  }

  public void testFieldInitializedWithLambda() {
    doTest();
  }

  public void testFieldUsedInJavadoc() {
    doTestConflict("Inlined field is used in javadoc");
  }

  public void testMultipleInitializers() {
    configureByFile("/refactoring/inlineConstantField/" + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(element instanceof PsiField);
    assertNull(InlineConstantFieldHandler.getInitializer((PsiField)element));
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean inlineThisOnly) {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineConstantField/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    assertTrue(element instanceof PsiField);
    PsiField field = (PsiField)element.getNavigationElement();
    new InlineConstantFieldProcessor(field, getProject(), refExpr, inlineThisOnly || element instanceof PsiCompiledElement).run();
    checkResultByFile(fileName + ".after");
  }

  private void doTestConflict(final String conflict) {
    try {
      doTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(conflict, e.getMessage());
    }
  }
}