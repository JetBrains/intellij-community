/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
