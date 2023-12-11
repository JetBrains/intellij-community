// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LambdaParameterTypeCanBeSpecifiedInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.descriptor", infos);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InferLambdaParameterTypeVisitor();
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return new InferLambdaParameterTypeFix((String)infos[0]);
  }


  private static class InferLambdaParameterTypeVisitor extends BaseInspectionVisitor {
    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression lambdaExpression) {
      super.visitLambdaExpression(lambdaExpression);
      PsiParameterList parameterList = lambdaExpression.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) return;
      for (PsiParameter parameter : parameters) {
        if (parameter.getTypeElement() != null) {
          return;
        }
      }
      final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
      if (functionalInterfaceType != null &&
          LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType) != null) {
        final String inferredTypesText = LambdaUtil.createLambdaParameterListWithFormalTypes(functionalInterfaceType, lambdaExpression,
                                                                                             true);
        if (inferredTypesText != null) {
          PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(parameterList);
          if (PsiUtil.isJavaToken(nextElement, JavaTokenType.ARROW)) {
            registerErrorAtRange(parameterList, nextElement, inferredTypesText);
          }
          else {
            registerError(parameterList, inferredTypesText);
          }
        }
      }
    }
  }

  private static class InferLambdaParameterTypeFix extends PsiUpdateModCommandQuickFix {
    private final String mySignatureText;

    InferLambdaParameterTypeFix(String signatureText) {
      mySignatureText = signatureText;
    }

    @Nls
    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.quickfix", mySignatureText);
    }

    @Nls
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("lambda.parameter.type.can.be.specified.family.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiLambdaExpression lambda = PsiTreeUtil.getNonStrictParentOfType(element, PsiLambdaExpression.class);
      if (lambda != null) {
        LambdaUtil.specifyLambdaParameterTypes(lambda);
      }
    }
  }
}
