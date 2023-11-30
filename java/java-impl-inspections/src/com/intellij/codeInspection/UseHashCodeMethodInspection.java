// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class UseHashCodeMethodInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
        if (getHashCodeOperand(expression) != null) {
          holder.registerProblem(expression, JavaAnalysisBundle.message("inspection.message.can.be.replaced.with.long.hashcode"),
                                 new ReplaceWithLongHashCodeFix());
        }
      }
    };
  }

  private static @Nullable PsiExpression getHashCodeOperand(PsiTypeCastExpression typeCastExpression) {
    if (typeCastExpression == null) return null;
    if (!PsiTypes.intType().equals(typeCastExpression.getType())) return null;
    PsiExpression operand = PsiUtil.skipParenthesizedExprDown(typeCastExpression.getOperand());
    if (!(operand instanceof PsiBinaryExpression binaryExpression)) return null;

    PsiJavaToken operationSign = binaryExpression.getOperationSign();
    if (operationSign.getTokenType() != JavaTokenType.XOR) return null;


    PsiExpression leftOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
    PsiExpression rightOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());

    if (leftOperand == null || rightOperand == null) return null;
    if (!PsiTypes.longType().equals(leftOperand.getType())) return null;

    if (isXorShift(leftOperand, rightOperand)) return leftOperand;
    if (isXorShift(rightOperand, leftOperand)) return rightOperand;
    return null;
  }

  private static boolean isXorShift(@NotNull PsiExpression leftOperand, @NotNull PsiExpression rightOperand) {
    if (rightOperand instanceof PsiBinaryExpression shiftingExpression) {
      if (shiftingExpression.getOperationSign().getTokenType() != JavaTokenType.GTGTGT) return false;

      PsiExpression leftSubOperand = shiftingExpression.getLOperand();
      return EquivalenceChecker.getCanonicalPsiEquivalence()
               .expressionsAreEquivalent(leftOperand, leftSubOperand) &&
             Objects.equals(32, ExpressionUtils.computeConstantExpression(shiftingExpression.getROperand()));
    }

    return false;
  }

  public static class ReplaceWithLongHashCodeFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Long.hashCode()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiTypeCastExpression element = (PsiTypeCastExpression)startElement;
      PsiExpression operand = getHashCodeOperand(element);
      if (operand != null) {
        CommentTracker ct = new CommentTracker();
        ct.replace(element, "Long.hashCode(" + ct.text(operand) + ")");
      }
    }
  }
}