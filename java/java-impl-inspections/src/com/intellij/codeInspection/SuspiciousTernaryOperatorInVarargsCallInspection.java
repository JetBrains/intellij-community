// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.psi.PsiAdapter;

public final class SuspiciousTernaryOperatorInVarargsCallInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList.isEmpty()) return;

        final PsiExpression[] args = argumentList.getExpressions();
        PsiExpression varargsExpression = PsiUtil.skipParenthesizedExprDown(ArrayUtil.getLastElement(args));
        final PsiConditionalExpression conditional = ObjectUtils.tryCast(varargsExpression, PsiConditionalExpression.class);
        if (conditional == null) return;

        final PsiMethod method = expression.resolveMethod();
        if (method == null || !method.isVarArgs()) return;

        final PsiParameter[] params = method.getParameterList().getParameters();
        if (args.length != params.length) return;

        final PsiExpression thenExpression = conditional.getThenExpression();
        final PsiExpression elseExpression = conditional.getElseExpression();
        if (thenExpression == null || elseExpression == null) return;

        final PsiType thenType = thenExpression.getType();
        final PsiType elseType = elseExpression.getType();
        boolean isThenArray = thenType instanceof PsiArrayType;
        if (isThenArray == elseType instanceof PsiArrayType) return;

        final PsiExpression nonArray = isThenArray ? elseExpression : thenExpression;
        final PsiExpression array = isThenArray ? thenExpression : elseExpression;

        PsiClassType varargsType = ObjectUtils.tryCast(varargsExpression.getType(), PsiClassType.class);
        if (varargsType == null) return;

        String typeName = varargsType.getName();
        final String replacementText = String.format("new %s[]{%s}", typeName, nonArray.getText());
        final LocalQuickFix[] fix = PsiAdapter.isPrimitiveArrayType(array.getType()) ? LocalQuickFix.EMPTY_ARRAY :
                                  new LocalQuickFix[]{new WrapInArrayInitializerFix(replacementText, typeName)};

        holder.registerProblem(nonArray,
                               JavaBundle.message("inspection.suspicious.ternary.in.varargs.description"),
                               ProblemHighlightType.WARNING,
                               fix);
      }

    };
  }

  private static class WrapInArrayInitializerFix extends PsiUpdateModCommandQuickFix {

    private final String myReplacementMessage;
    private final String myTypeName;

    WrapInArrayInitializerFix(String replacementMessage, String typeName) {
      myReplacementMessage = replacementMessage;
      myTypeName = typeName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myReplacementMessage);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.suspicious.ternary.in.varargs.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      CommentTracker ct = new CommentTracker();
      final String replacementText = String.format("new %s[]{%s}", myTypeName, ct.text(element));
      ct.replaceAndRestoreComments(element, replacementText);
    }
  }
}
