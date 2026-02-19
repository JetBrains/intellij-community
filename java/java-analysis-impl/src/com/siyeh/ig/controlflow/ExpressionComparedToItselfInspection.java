// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ExpressionComparedToItselfInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean ignoreSideEffectConditions = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSideEffectConditions", InspectionGadgetsBundle.message("duplicate.condition.ignore.method.calls.option"))
        .description(InspectionGadgetsBundle.message("duplicate.condition.ignore.method.calls.option.description")));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (!ComparisonUtils.isComparisonOperation(tokenType)) return;
        PsiExpression leftOperand = expression.getLOperand();
        PsiExpression rightOperand = expression.getROperand();
        if (rightOperand == null) return;
        boolean equivalent = EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(leftOperand, rightOperand);
        if (!equivalent) return;
        if (PsiUtil.isConstantExpression(leftOperand)) return;
        // Do not repeat the warnings already reported by "Constant values" inspection
        if (Boolean.TRUE.equals(CommonDataflow.computeValue(expression))) return;
        ThreeState wantedStatus = ignoreSideEffectConditions ? ThreeState.UNSURE : ThreeState.YES;
        ThreeState actualStatus = SideEffectChecker.getSideEffectStatus(leftOperand);
        if (actualStatus.isAtLeast(wantedStatus)) return;
        ModCommandAction fix = null;
        if (actualStatus == ThreeState.UNSURE) {
          fix = new UpdateInspectionOptionFix(
            ExpressionComparedToItselfInspection.this, "ignoreSideEffectConditions",
            JavaAnalysisBundle.message("intention.name.do.not.report.conditions.with.possible.side.effect"), true);
        }
        holder.problem(expression.getOperationSign(),
                       JavaAnalysisBundle.message("inspection.message.expression.compared.to.itself.description"))
          .maybeFix(fix).register();
      }
    };
  }
}
