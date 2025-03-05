// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public final class RedundantLambdaCodeBlockInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(RedundantLambdaCodeBlockInspection.class);
  private static final @NonNls String SHORT_NAME = "CodeBlock2Expr";

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

    @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.LAMBDA_EXPRESSIONS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        final PsiElement body = expression.getBody();
        final PsiExpression psiExpression = isCodeBlockRedundant(body);
        if (psiExpression != null) {
          final PsiElement errorElement;
          final PsiElement parent = psiExpression.getParent();
          if (parent instanceof PsiReturnStatement) {
            errorElement = parent.getFirstChild();
          } else {
            errorElement = body.getFirstChild();
          }
          holder.registerProblem(errorElement, JavaAnalysisBundle.message("statement.lambda.can.be.replaced.with.expression.lambda"), new ReplaceWithExprFix());
        }
      }
    };
  }

  public static PsiExpression isCodeBlockRedundant(PsiElement body) {
    if (body instanceof PsiCodeBlock) {
      PsiExpression psiExpression = LambdaUtil.extractSingleExpressionFromBody(body);
      if (psiExpression != null && !findCommentsOutsideExpression(body, psiExpression)) {
        if (LambdaUtil.isExpressionStatementExpression(psiExpression) &&
            !LambdaUtil.isSafeLambdaBodyReplacement((PsiLambdaExpression)body.getParent(), () -> psiExpression)) {
          return null;
        }
        return psiExpression;
      }
    }
    return null;
  }

  private static boolean findCommentsOutsideExpression(PsiElement body, PsiExpression psiExpression) {
    final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(body, PsiComment.class);
    for (PsiComment comment : comments) {
      if (!PsiTreeUtil.isAncestor(psiExpression, comment, true) && !isSelfSuppressionComment(comment)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSelfSuppressionComment(PsiComment comment) {
    String suppressString = JavaSuppressionUtil.getSuppressedInspectionIdsIn(comment);
    if (suppressString != null) {
      String[] suppressIds = suppressString.split(",");
      if (suppressIds.length == 1 && SHORT_NAME.equals(suppressIds[0])) {
        return true;
      }
    }
    return false;
  }

  private static class ReplaceWithExprFix extends PsiUpdateModCommandQuickFix implements HighPriorityAction {
    @Override
    public @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("replace.with.expression.lambda");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
      if (lambdaExpression != null) {
        final PsiElement body = lambdaExpression.getBody();
        if (body != null) {
          PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
          if (expression != null) {
            body.replace(expression);
          }
        }
      }
    }
  }
}
