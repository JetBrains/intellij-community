// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineObjectProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InlineObjectTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInlinePoint() { doTest(); }
  public void testInlinePointToString() { doTest(); }
  public void testInlineBitString() { doTest(); }
  public void testInlineSideEffect() { doTest(); }
  public void testRecordWithCompactConstructor() { doTest(); }
  public void testRecordWithCanonicalConstructor() { doTest(); }
  public void testInlineFileParentSrc() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(this::doTest);
  }

  @Override
  protected Sdk getProjectJDK() {
    return getTestName(false).contains("Src") ? IdeaTestUtil.getMockJdk17() : super.getProjectJDK();
  }

  private void doTest() {
    @NonNls String fileName = configure();
    performAction();
    checkResultByFile(fileName + ".after");
  }

  @NotNull
  private String configure() {
    @NonNls String fileName = "/refactoring/inlineObject/" + getTestName(false) + ".java";
    configureByFile(fileName);
    return fileName;
  }

  private void performAction() {
    final PsiReference ref = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    assertInstanceOf(ref, PsiJavaCodeReferenceElement.class);
    final PsiElement parent = ((PsiJavaCodeReferenceElement)ref).getParent();
    assertInstanceOf(parent, PsiNewExpression.class);
    PsiMethod method = ((PsiNewExpression)parent).resolveConstructor();
    method = (PsiMethod)method.getNavigationElement();
    InlineObjectProcessor processor = InlineObjectProcessor.create(ref, method);
    assertNotNull(processor);
    processor.run();
  }
}
