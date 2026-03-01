// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiResourceList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaBaseRefactoringSupportProvider;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.IntroduceFunctionalParameterHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureHandler;
import com.intellij.refactoring.extractInterface.ExtractInterfaceHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractSuperclass.ExtractSuperclassHandler;
import com.intellij.refactoring.extractclass.ExtractClassHandler;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.introduceVariable.IntroduceFunctionalVariableHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import com.intellij.refactoring.memberPullUp.JavaPullUpHandler;
import com.intellij.refactoring.memberPushDown.JavaPushDownHandler;
import com.intellij.refactoring.typeMigration.ChangeTypeSignatureHandler;
import com.intellij.refactoring.typeMigration.ChangeTypeSignatureHandlerBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaRefactoringSupportProvider extends JavaBaseRefactoringSupportProvider {
  @Override
  public boolean isSafeDeleteAvailable(@NotNull PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField ||
           (element instanceof PsiParameter p && p.getDeclarationScope() instanceof PsiMethod) ||
           element instanceof PsiPackage || element instanceof PsiLocalVariable || element instanceof PsiRecordComponent;
  }

  @Override
  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new IntroduceConstantHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new IntroduceFieldHandler();
  }

  @Override
  public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
    return mayRenameInplace(element, context);
  }

  @Override
  public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement elementToRename, @Nullable PsiElement context) {
    if (context != null && context.getLanguage() != elementToRename.getLanguage() &&
        elementToRename instanceof PsiNamedElement namedElement) {
      String name = namedElement.getName();
      if (name != null && !LanguageNamesValidation.isIdentifier(context.getLanguage(), name, elementToRename.getProject())) {
        return false;
      }
    }
    if (context instanceof PsiKeyword) return false;

    if (elementToRename instanceof SyntheticElement) {
      if (elementToRename instanceof PsiMethod method) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null || aClass.isEnum()) return false;
      }
    }
    return elementToRename instanceof PsiMember || elementToRename instanceof PsiJavaModule || isCanonicalConstructorParameter(elementToRename);
  }

  @Override
  public @NotNull JavaIntroduceVariableHandlerBase getIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  @Override
  public @NotNull ChangeTypeSignatureHandlerBase getChangeTypeSignatureHandler() {
    return new ChangeTypeSignatureHandler();
  }

  @Override
  public @Nullable RefactoringActionHandler getExtractMethodHandler() {
    return new ExtractMethodHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new IntroduceParameterHandler();
  }

  @Override
  public @Nullable RefactoringActionHandler getIntroduceFunctionalParameterHandler() {
    return new IntroduceFunctionalParameterHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceFunctionalVariableHandler() {
    return new IntroduceFunctionalVariableHandler();
  }

  @Override
  public RefactoringActionHandler getPullUpHandler() {
    return new JavaPullUpHandler();
  }

  @Override
  public RefactoringActionHandler getPushDownHandler() {
    return new JavaPushDownHandler();
  }

  @Override
  public RefactoringActionHandler getExtractInterfaceHandler() {
    return new ExtractInterfaceHandler();
  }

  @Override
  public RefactoringActionHandler getExtractSuperClassHandler() {
    return new ExtractSuperclassHandler();
  }

  @Override
  public ChangeSignatureHandler getChangeSignatureHandler() {
    return new JavaChangeSignatureHandler();
  }

  @Override
  public RefactoringActionHandler getExtractClassHandler() {
    return new ExtractClassHandler();
  }

  @Override
  public boolean isInplaceIntroduceAvailable(@NotNull PsiElement element, PsiElement context) {
    if (!(element instanceof PsiExpression)) return false;
    if (context == null) return false;
    try {
      PsiElementFactory.getInstance(element.getProject()).createExpressionFromText(element.getText(), element);
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    return true;
  }

  public static boolean mayRenameInplace(PsiElement elementToRename, PsiElement nameSuggestionContext) {
    if (nameSuggestionContext != null && nameSuggestionContext.getContainingFile() != elementToRename.getContainingFile()) return false;
    if (elementToRename instanceof PsiImplicitClass) return false;
    if (!PsiUtil.isJvmLocalVariable(elementToRename) && !(elementToRename instanceof PsiLabeledStatement)) {
      return false;
    }
    SearchScope useScope = PsiSearchHelper.getInstance(elementToRename.getProject()).getUseScope(elementToRename);
    if (!(useScope instanceof LocalSearchScope scope)) return false;
    PsiElement[] scopeElements = scope.getScope();
    if (scopeElements.length == 0 && elementToRename instanceof PsiVariable variable && variable.isUnnamed()) return true;
    if (scopeElements.length > 1 &&                          // assume there are no elements with use scopes with holes in them
        !isElementWithComment(scopeElements) &&              // ... except a case of element and it's doc comment
        !isResourceVariable(scopeElements)) {
      return false;    // ... and badly scoped resource variables
    }
    if (isCanonicalConstructorParameter(elementToRename)) return false;
    PsiFile containingFile = elementToRename.getContainingFile();
    return PsiTreeUtil.isAncestor(containingFile, scopeElements[0], false);
  }

  private static boolean isElementWithComment(PsiElement[] scopeElements) {
    if (scopeElements.length > 2) return false;

    PsiDocComment comment = null;
    PsiDocCommentOwner owner = null;
    for (PsiElement element : scopeElements) {
      if (element instanceof PsiDocComment docComment) {
        comment = docComment;
      }
      else if (element instanceof PsiDocCommentOwner commentOwner) owner = commentOwner;
    }

    return comment != null && comment.getOwner() == owner;
  }

  private static boolean isResourceVariable(PsiElement[] scopeElements) {
    return scopeElements.length == 2 && scopeElements[0] instanceof PsiResourceList && scopeElements[1] instanceof PsiCodeBlock;
  }

  private static boolean isCanonicalConstructorParameter(@NotNull PsiElement elementToRename) {
    if (!(elementToRename instanceof PsiParameter)) return false;
    PsiMethod method = PsiTreeUtil.getParentOfType(elementToRename.getParent(), PsiMethod.class);
    if (method == null) return false;
    return JavaPsiRecordUtil.isExplicitCanonicalConstructor(method);
  }
}
