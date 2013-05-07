/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: cdr
 */
public class NumericOverflowInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Key<String> HAS_OVERFLOW_IN_CHILD = Key.create("HAS_OVERFLOW_IN_CHILD");

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.NUMERIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Numeric overflow";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "NumericOverflow";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        boolean info = hasOverflow(expression, holder.getProject());
        if (info) {
          holder.registerProblem(expression, JavaErrorMessages.message("numeric.overflow.in.expression"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    };
  }

  private static boolean hasOverflow(PsiExpression expr, @NotNull Project project) {
    if (!TypeConversionUtil.isNumericType(expr.getType())) return false;
    boolean overflow = false;
    try {
      if (expr.getUserData(HAS_OVERFLOW_IN_CHILD) == null) {
        JavaPsiFacade.getInstance(project).getConstantEvaluationHelper().computeConstantExpression(expr, true);
      }
      else {
        overflow = true;
      }
    }
    catch (ConstantEvaluationOverflowException e) {
      overflow = true;
    }
    finally {
      PsiElement parent = expr.getParent();
      if (overflow && parent instanceof PsiExpression) {
        parent.putUserData(HAS_OVERFLOW_IN_CHILD, "");
      }
    }

    return overflow;
  }

}
