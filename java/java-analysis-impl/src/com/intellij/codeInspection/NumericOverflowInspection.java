// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 * @author cdr
 */
public class NumericOverflowInspection extends AbstractBaseJavaLocalInspectionTool {
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
    if (!TypeConversionUtil.isNumericType(expr.getType())) {
      return false;
    }

    boolean result = false;
    boolean toStoreInParent = false;
    try {
      if (expr.getUserData(HAS_OVERFLOW_IN_CHILD) == null) {
        JavaPsiFacade.getInstance(project).getConstantEvaluationHelper().computeConstantExpression(expr, true);
      }
      else {
        toStoreInParent = true;
        expr.putUserData(HAS_OVERFLOW_IN_CHILD, null);
      }
    }
    catch (ConstantEvaluationOverflowException e) {
      result = toStoreInParent = true;
    }
    finally {
      PsiElement parent = expr.getParent();
      if (toStoreInParent && parent instanceof PsiExpression) {
        parent.putUserData(HAS_OVERFLOW_IN_CHILD, "");
      }
    }

    return result;
  }
}