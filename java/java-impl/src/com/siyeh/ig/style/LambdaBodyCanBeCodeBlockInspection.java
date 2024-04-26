// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class LambdaBodyCanBeCodeBlockInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.LAMBDA_EXPRESSIONS);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OneLineLambda2CodeBlockVisitor();
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return new OneLineLambda2CodeBlockFix();
  }

  private static class OneLineLambda2CodeBlockVisitor extends BaseInspectionVisitor {
    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression lambdaExpression) {
      super.visitLambdaExpression(lambdaExpression);
      if (lambdaExpression.getBody() instanceof PsiExpression) {
        registerError(lambdaExpression);
      }
    }
  }

  private static class OneLineLambda2CodeBlockFix extends PsiUpdateModCommandQuickFix {
    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiLambdaExpression) {
        CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)element);
      }
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("lambda.body.can.be.code.block.quickfix");
    }
  }
}
