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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 */
public class RedundantLambdaCodeBlockInspection extends BaseJavaLocalInspectionTool {
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
    return "Lambda code block can be replaced with expression";
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
          final PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
          if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
            final PsiReturnStatement returnStatement = (PsiReturnStatement)statements[0];
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue != null) {
              holder.registerProblem(returnStatement.getFirstChild(), "Lambda code block can be replaced with one line expression",
                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL, new ReplaceWithExprFix());
            }
          }
        }
      }
    };
  }

  private static class ReplaceWithExprFix implements LocalQuickFix, HighPriorityAction {
    @NotNull
    @Override
    public String getName() {
      return "Replace with one line expression";
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
          PsiElement body = lambdaExpression.getBody();
          LOG.assertTrue(body != null);
          PsiExpression returnValue = ((PsiReturnStatement)((PsiCodeBlock)body).getStatements()[0]).getReturnValue();
          LOG.assertTrue(returnValue != null);
          body.replace(returnValue);
        }
      }
    }
  }
}
