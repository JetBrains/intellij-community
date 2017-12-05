// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RedundantLambdaCodeBlockInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(RedundantLambdaCodeBlockInspection.class);

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Statement lambda can be replaced with expression lambda";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return  "CodeBlock2Expr";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        if (PsiUtil.isLanguageLevel8OrHigher(expression)) {
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
            holder.registerProblem(errorElement, "Statement lambda can be replaced with expression lambda",
                                   ProblemHighlightType.LIKE_UNUSED_SYMBOL, new ReplaceWithExprFix());
          }
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
      if (!PsiTreeUtil.isAncestor(psiExpression, comment, true)) {
        return true;
      }
    }
    return false;
  }

  private static class ReplaceWithExprFix implements LocalQuickFix, HighPriorityAction {
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with expression lambda";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
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
}
