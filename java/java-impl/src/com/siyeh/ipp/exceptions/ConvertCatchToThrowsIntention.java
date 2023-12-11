// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.exceptions;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class ConvertCatchToThrowsIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("convert.catch.to.throws.intention.family.name");
  }

  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("convert.catch.to.throws.intention.name");
  }

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ConvertCatchToThrowsPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiCatchSection catchSection = (PsiCatchSection)element.getParent();
    final NavigatablePsiElement owner = PsiTreeUtil.getParentOfType(catchSection, PsiMethod.class, PsiLambdaExpression.class);
    final PsiMethod method;
    if (owner instanceof PsiMethod) {
      method = (PsiMethod)owner;
    }
    else if (owner instanceof PsiLambdaExpression) {
      method = LambdaUtil.getFunctionalInterfaceMethod(owner);
    }
    else {
      return;
    }
    if (method == null) {
      return;
    }
    // todo warn if method implements or overrides some base method
    //             Warning
    // "Method xx() of class XX implements/overrides method of class
    // YY. Do you want to modify the base method?"
    //                                             [Yes][No][Cancel]
    addToThrowsList(method.getThrowsList(), catchSection.getCatchType());
    final PsiTryStatement tryStatement = catchSection.getTryStatement();
    if (tryStatement.getCatchSections().length > 1 || tryStatement.getResourceList() != null || tryStatement.getFinallyBlock() != null) {
      catchSection.delete();
    }
    else {
      BlockUtils.unwrapTryBlock(tryStatement);
    }
  }

  private static void addToThrowsList(PsiReferenceList throwsList, PsiType catchType) {
    if (catchType instanceof PsiClassType classType) {
      final PsiClassType[] types = throwsList.getReferencedTypes();
      for (PsiClassType type : types) {
        if (catchType.equals(type)) {
          return;
        }
      }
      final Project project = throwsList.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(classType);
      throwsList.add(referenceElement);
    }
    else if (catchType instanceof PsiDisjunctionType disjunctionType) {
      final List<PsiType> disjunctions = disjunctionType.getDisjunctions();
      for (PsiType disjunction : disjunctions) {
        addToThrowsList(throwsList, disjunction);
      }
    }
  }
}