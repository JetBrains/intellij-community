// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class SuspiciousTernaryOperatorInVarargsCallInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiMethod method = expression.resolveMethod();
        if (method == null || !method.isVarArgs()) return;

        final PsiExpression[] args = expression.getArgumentList().getExpressions();
        final PsiParameter[] params = method.getParameterList().getParameters();
        if (args.length != params.length) return;

        int varargsPosition = args.length - 1;
        PsiExpression varargsExpression = args[varargsPosition];
        final PsiConditionalExpression conditional = ObjectUtils.tryCast(varargsExpression, PsiConditionalExpression.class);
        if (conditional == null) return;

        final PsiExpression thenExpression = conditional.getThenExpression();
        final PsiExpression elseExpression = conditional.getElseExpression();
        if (thenExpression == null || elseExpression == null) return;

        final PsiType thenType = thenExpression.getType();
        final PsiType elseType = elseExpression.getType();
        boolean isThenArray = thenType instanceof PsiArrayType;
        if (isThenArray == elseType instanceof PsiArrayType) return;

        final PsiExpression nonArray = isThenArray ? elseExpression : thenExpression;
        PsiClassType varargsType = ObjectUtils.tryCast(varargsExpression.getType(), PsiClassType.class);
        if (varargsType == null) return;

        final String replacementText = String.format("new %s[]{%s}", varargsType.getName(), nonArray.getText());

        holder.registerProblem(nonArray,
                               JavaBundle.message("inspection.suspicious.ternary.in.varargs.description"),
                               ProblemHighlightType.WARNING,
                               new WrapInArrayInitializerFix(replacementText));
      }

    };
  }

  private static class WrapInArrayInitializerFix implements LocalQuickFix {

    private final String myReplacementText;

    WrapInArrayInitializerFix(String replacementText) {
      myReplacementText = replacementText;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myReplacementText);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.suspicious.ternary.in.varargs.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null || !element.isValid()) return;

      CommentTracker commentTracker = new CommentTracker();
      commentTracker.replaceAndRestoreComments(element, myReplacementText);
    }
  }
}
