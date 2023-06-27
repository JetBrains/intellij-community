// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

final class RemoveNewKeywordFix extends PsiUpdateModCommandAction<PsiNewExpression> {
  RemoveNewKeywordFix(@NotNull PsiNewExpression outerParent) {super(outerParent);}

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.remove.new.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNewExpression newDeclaration, @NotNull ModPsiUpdater updater) {
    PsiJavaCodeReferenceElement reference = newDeclaration.getClassOrAnonymousClassReference();
    if (reference == null) return;
    PsiElement qualifier = reference.getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement referenceElement)) return;
    CommentTracker ct = new CommentTracker();
    PsiReferenceParameterList parameterList = referenceElement.getParameterList();
    if (parameterList != null) {
      ct.delete(parameterList);
    }

    ct.markRangeUnchanged(reference, Objects.requireNonNullElse(newDeclaration.getArgumentList(), reference));

    String methodCallExpression = newDeclaration.getText().substring(reference.getStartOffsetInParent());
    ct.replaceAndRestoreComments(newDeclaration, methodCallExpression);
  }
}
