/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.java;

import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.extractInterface.ExtractInterfaceHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractSuperclass.ExtractSuperclassHandler;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.memberPullUp.JavaPullUpHandler;
import com.intellij.refactoring.memberPushDown.JavaPushDownHandler;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class JavaRefactoringSupportProvider extends DefaultRefactoringSupportProvider {
  public boolean isSafeDeleteAvailable(PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField ||
           (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) ||
           element instanceof PsiPackage || element instanceof PsiLocalVariable;
  }

  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new IntroduceConstantHandler();
  }

  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new IntroduceFieldHandler();
  }

  public boolean doInplaceRenameFor(final PsiElement element, final PsiElement context) {
    return mayRenameInplace(element, context);
  }

  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  @Nullable
  public RefactoringActionHandler getExtractMethodHandler() {
    return new ExtractMethodHandler();
  }

  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new IntroduceParameterHandler();
  }

  public RefactoringActionHandler getPullUpHandler() {
    return new JavaPullUpHandler();
  }

  public RefactoringActionHandler getPushDownHandler() {
    return new JavaPushDownHandler();
  }

  public RefactoringActionHandler getExtractModuleHandler() {
    return new ExtractInterfaceHandler();
  }

  public RefactoringActionHandler getExtractSuperClassHandler() {
    return new ExtractSuperclassHandler();
  }

  public static boolean mayRenameInplace(PsiElement elementToRename, final PsiElement nameSuggestionContext) {
    if (!(elementToRename instanceof PsiVariable)) return false;
    if (nameSuggestionContext != null && nameSuggestionContext.getContainingFile() != elementToRename.getContainingFile()) return false;
    if (!(elementToRename instanceof PsiLocalVariable) && !(elementToRename instanceof PsiParameter)) return false;
    SearchScope useScope = elementToRename.getUseScope();
    if (!(useScope instanceof LocalSearchScope)) return false;
    PsiElement[] scopeElements = ((LocalSearchScope) useScope).getScope();
    if (scopeElements.length > 1 &&                          // assume there are no elements with use scopes with holes in'em
        !isElementWithComment(scopeElements)) return false;  // except a case of element and it's doc comment
    PsiFile containingFile = elementToRename.getContainingFile();
    return PsiTreeUtil.isAncestor(containingFile, scopeElements[0], false);
  }

  private static boolean isElementWithComment(final PsiElement[] scopeElements) {
    if (scopeElements.length > 2) return false;

    PsiDocComment comment = null;
    PsiDocCommentOwner owner = null;
    for (PsiElement element : scopeElements) {
      if (element instanceof PsiDocComment) comment = (PsiDocComment)element;
      else if (element instanceof PsiDocCommentOwner) owner = (PsiDocCommentOwner)element;
    }

    return comment != null && comment.getOwner() == owner;
  }
}
