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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        final PsiElement body = expression.getBody();
        if (body instanceof PsiCodeBlock) {
          PsiExpression psiExpression = getExpression((PsiCodeBlock)body);
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

  @Nullable
  private static PsiExpression getExpression(PsiCodeBlock body) {
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 1) {
      if (statements[0] instanceof PsiBlockStatement) {
        return getExpression(((PsiBlockStatement)statements[0]).getCodeBlock());
      }
      if (statements[0] instanceof PsiReturnStatement || statements[0] instanceof PsiExpressionStatement) {
        if (statements[0] instanceof PsiReturnStatement) {
          final PsiReturnStatement returnStatement = (PsiReturnStatement)statements[0];
          return returnStatement.getReturnValue();
        }
        else {
          final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
          final PsiType psiType = expression.getType();
          if (psiType != PsiType.VOID) {
            return null;
          }
          return expression;
        }
      }
    }
    return null;
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
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
        if (lambdaExpression != null) {
          final PsiElement body = lambdaExpression.getBody();
          if (body != null) {
            PsiExpression expression = getExpression((PsiCodeBlock)body);
            if (expression != null) {
              body.replace(expression);
            }
          }
        }
      }
    }
  }
}
