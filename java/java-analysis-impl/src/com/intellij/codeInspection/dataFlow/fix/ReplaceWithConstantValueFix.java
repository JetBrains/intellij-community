// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.extractMethod.ExtractMethodUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithConstantValueFix extends PsiUpdateModCommandQuickFix {
  private final String myPresentableName;
  private final String myReplacementText;

  public ReplaceWithConstantValueFix(String presentableName, String replacementText) {
    myPresentableName = presentableName;
    myReplacementText = replacementText;
  }

  @NotNull
  @Override
  public String getName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", myPresentableName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("replace.with.constant.value");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement problemElement, @NotNull ModPsiUpdater updater) {
    PsiMethodCallExpression call = problemElement.getParent() instanceof PsiExpressionList &&
                                   problemElement.getParent().getParent() instanceof PsiMethodCallExpression ?
                                   (PsiMethodCallExpression)problemElement.getParent().getParent() :
                                   null;
    PsiMethod targetMethod = null;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (call != null) {
      JavaResolveResult result = call.resolveMethodGenerics();
      substitutor = result.getSubstitutor();
      targetMethod = ObjectUtils.tryCast(result.getElement(), PsiMethod.class);
    }

    new CommentTracker().replaceAndRestoreComments(problemElement, myReplacementText);

    if (targetMethod != null) {
      ExtractMethodUtil.addCastsToEnsureResolveTarget(targetMethod, substitutor, call);
    }
  }
}
