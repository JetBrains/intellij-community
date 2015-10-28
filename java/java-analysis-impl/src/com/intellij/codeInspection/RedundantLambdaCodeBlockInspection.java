/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * User: anna
 */
public class RedundantLambdaCodeBlockInspection extends BaseJavaBatchLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + RedundantLambdaCodeBlockInspection.class.getName());

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
          final PsiExpression psiExpression = isCodeBlockRedundant(expression, body);
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

  public static PsiExpression isCodeBlockRedundant(PsiExpression expression, PsiElement body) {
    if (body instanceof PsiCodeBlock) {
      PsiExpression psiExpression = LambdaUtil.extractSingleExpressionFromBody(body);
      if (psiExpression != null && !findCommentsOutsideExpression(body, psiExpression)) {
        if (LambdaUtil.isExpressionStatementExpression(psiExpression)) {
          final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
          if (parent instanceof PsiExpressionList) {
            final PsiElement gParent = parent.getParent();
            if (gParent instanceof PsiCallExpression) {
              final CandidateInfo[] candidates = PsiResolveHelper.SERVICE.getInstance(gParent.getProject())
                .getReferencedMethodCandidates((PsiCallExpression)gParent, false, true);
              if (candidates.length > 1) {
                final List<CandidateInfo> info = new ArrayList<CandidateInfo>(Arrays.asList(candidates));
                final LanguageLevel level = PsiUtil.getLanguageLevel(parent);
                final JavaMethodsConflictResolver conflictResolver = new JavaMethodsConflictResolver((PsiExpressionList)parent, level);
                final PsiExpressionList argumentList = ((PsiCallExpression)gParent).getArgumentList();
                if (argumentList == null) {
                  return null;
                }
                final boolean atLeastOneMatchFound = conflictResolver.checkParametersNumber(info, argumentList.getExpressions().length, false);
                if (!atLeastOneMatchFound) {
                  return null;
                }
                conflictResolver.checkSpecifics(info, MethodCandidateInfo.ApplicabilityLevel.VARARGS, level);
                if (info.size() > 1) {
                  return null;
                }
              }
            }
          }
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
    public String getName() {
      return "Replace with expression lambda";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
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
