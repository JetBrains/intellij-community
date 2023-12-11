// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public final class NewObjectEqualityInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    Object sign = infos[1];
    return InspectionGadgetsBundle.message("inspection.new.object.equality.message", sign);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NumberEqualityVisitor();
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    return EqualityToEqualsFix.buildEqualityFixes((PsiBinaryExpression)infos[0]);
  }

  private static class NumberEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) return;
      final PsiExpression lhs = expression.getLOperand();
      if (isNewObject(rhs) && !(lhs.getType() instanceof PsiPrimitiveType)) {
        registerError(rhs, expression, expression.getOperationSign().getText());
      }
      else if (isNewObject(lhs) && !(rhs.getType() instanceof PsiPrimitiveType)) {
        registerError(lhs, expression, expression.getOperationSign().getText());
      }
    }

    @Contract("null -> false")
    private static boolean isNewObject(PsiExpression expression) {
      expression = resolveExpression(expression);
      if (expression instanceof PsiNewExpression) return true;
      if (expression instanceof PsiMethodCallExpression) {
        List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts((PsiCallExpression)expression);
        return ContractReturnValue.returnNew().equals(JavaMethodContractUtil.getNonFailingReturnValue(contracts));
      }
      return false;
    }

    private static PsiExpression resolveExpression(PsiExpression expression) {
      PsiElement parent = expression.getParent();
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiReferenceExpression reference)) return expression;
      PsiLocalVariable variable = tryCast(reference.resolve(), PsiLocalVariable.class);
      if (variable == null) return expression;
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null) return expression;
      if (parent instanceof PsiBinaryExpression) {
        // Check if variable is reused in the same expression
        if (VariableAccessUtils.getVariableReferences(variable, parent).size() != 1) return expression;
      }
      PsiElement block = ControlFlowUtil.findCodeFragment(variable);
      PsiElement expressionContext = PsiTreeUtil.getParentOfType(expression, PsiMember.class, PsiLambdaExpression.class);
      if (expressionContext == null || PsiTreeUtil.isAncestor(block, expressionContext, true)) return expression;
      if (!HighlightControlFlowUtil.isEffectivelyFinal(variable, block, null)) return expression;
      ControlFlow flow;
      try {
        flow = ControlFlowFactory.getInstance(block.getProject()).getControlFlow(block, new LocalsControlFlowPolicy(block), false);
      }
      catch (AnalysisCanceledException e) {
        return expression;
      }
      int initializerEnd = flow.getEndOffset(initializer);
      int start = initializerEnd + 1;
      if (ControlFlowUtils.isVariableReferencedBeforeStatementEntry(flow, start, expression, variable, Set.of(initializerEnd))) {
        return expression;
      }
      return initializer;
    }
  }
}