// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RemoveRedundantPolyadicOperandFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PointlessNullCheckInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    PsiMethodCallExpression parent =
      Objects.requireNonNull(PsiTreeUtil.getParentOfType((PsiElement)infos[1], PsiMethodCallExpression.class));
    return InspectionGadgetsBundle.message("pointless.nullcheck.problem.descriptor.call",
                                           parent.getMethodExpression().getReferenceName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessNullCheckVisitor();
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    return new RemoveRedundantPolyadicOperandFix(expression.getText());
  }

  private static class PointlessNullCheckVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType operationTokenType = expression.getOperationTokenType();
      if (operationTokenType.equals(JavaTokenType.ANDAND)) {
        checkAndChain(expression);
      }
      else if (operationTokenType.equals(JavaTokenType.OROR)) {
        checkOrChain(expression);
      }
    }

    private void checkOrChain(PsiPolyadicExpression expression) {
      final PsiExpression[] operands = expression.getOperands();
      for (int i = 0; i < operands.length - 1; i++) {
        if (!(PsiUtil.skipParenthesizedExprDown(operands[i]) instanceof PsiBinaryExpression binaryExpression)) continue;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.EQEQ)) continue;

        for (int j = i + 1; j < operands.length; j++) {
          final PsiExpression implicitCheckCandidate = BoolUtils.getNegated(PsiUtil.skipParenthesizedExprDown(operands[j]));
          if (checkExpressions(operands, i, j, binaryExpression, implicitCheckCandidate)) {
            return;
          }
        }
      }
    }

    private void checkAndChain(PsiPolyadicExpression expression) {
      final PsiExpression[] operands = expression.getOperands();
      for (int i = 0; i < operands.length - 1; i++) {
        if (!(PsiUtil.skipParenthesizedExprDown(operands[i]) instanceof PsiBinaryExpression binaryExpression)) continue;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.NE)) continue;

        for (int j = i + 1; j < operands.length; j++) {
          PsiExpression implicitCheckCandidate = PsiUtil.skipParenthesizedExprDown(operands[j]);
          if (checkExpressions(operands, i, j, binaryExpression, implicitCheckCandidate)) {
            return;
          }
        }
      }
    }

    private boolean checkExpressions(PsiExpression[] operands,
                                     int i,
                                     int j,
                                     PsiBinaryExpression binaryExpression,
                                     PsiExpression implicitCheckCandidate) {
      final PsiReferenceExpression explicitCheckReference = getReferenceFromNullCheck(binaryExpression);
      if (explicitCheckReference == null || !(explicitCheckReference.resolve() instanceof PsiVariable variable)) return false;
      final PsiReferenceExpression implicitCheckReference = getReferenceFromImplicitNullCheckExpression(implicitCheckCandidate);
      if (implicitCheckReference == null || !implicitCheckReference.isReferenceTo(variable)) return false;
      if (isVariableUsed(operands, i, j, variable)) return false;
      registerError(binaryExpression, binaryExpression, implicitCheckReference);
      return true;
    }

    private static boolean isVariableUsed(PsiExpression[] operands, int i, int j, PsiVariable variable) {
      return Arrays.stream(operands, i + 1, j).anyMatch(op -> VariableAccessUtils.variableIsUsed(variable, op));
    }

    private static @Nullable PsiReferenceExpression getReferenceFromNullCheck(PsiBinaryExpression expression) {
      PsiExpression comparedWithNull = ExpressionUtils.getValueComparedWithNull(expression);
      return PsiUtil.skipParenthesizedExprDown(comparedWithNull) instanceof PsiReferenceExpression ref ? ref : null;
    }

    private @Nullable PsiReferenceExpression getReferenceFromImplicitNullCheckExpression(PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      PsiReferenceExpression checked = getReferenceFromBooleanCall(expression);
      return checked == null ? getReferenceFromOrChain(expression) : checked;
    }

    private static @Nullable PsiReferenceExpression getReferenceFromBooleanCall(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression call)) return null;
      if (!PsiTypes.booleanType().equals(call.getType())) return null;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier != null && SideEffectChecker.mayHaveSideEffects(qualifier)) return null;
      PsiMethod method = call.resolveMethod();
      if (method == null) return null;
      List<? extends MethodContract> contracts;
      if (MethodUtils.isEquals(method)) {
        // Contracts for equals are tuned for some classes to (this == arg#0) -> true; () -> false
        // so let's rewrite this to (null == arg#0) -> false which is more relevant to this inspection
        ValueConstraint[] nullArg = {ValueConstraint.NULL_VALUE};
        contracts = Collections.singletonList(new StandardMethodContract(nullArg, ContractReturnValue.returnFalse()));
      } else {
        contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
      }
      if (contracts.isEmpty() || !(contracts.get(0) instanceof StandardMethodContract contract)) return null;
      if (!contract.getReturnValue().equals(ContractReturnValue.returnFalse())) return null;
      ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
      if (condition == null) return null;
      int idx = condition.getNullCheckedArgument(true).orElse(-1);
      if (idx == -1) return null;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length <= idx || method.isVarArgs() && idx == args.length - 1) return null;
      if (!(args[idx] instanceof PsiReferenceExpression reference)) return null;
      if (!(reference.resolve() instanceof PsiVariable target)) return null;
      if (!SyntaxTraverser.psiTraverser(call).filter(PsiReference.class)
        .filter(ref -> !reference.equals(ref) && ref.isReferenceTo(target)).isEmpty()) {
        // variable is reused for something else
        return null;
      }
      if (ContainerUtil.or(args, SideEffectChecker::mayHaveSideEffects)) return null;
      return reference;
    }

    private @Nullable PsiReferenceExpression getReferenceFromOrChain(PsiExpression expression) {
      if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) return null;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.OROR != tokenType) return null;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final PsiReferenceExpression referenceExpression = getReferenceFromImplicitNullCheckExpression(operands[0]);
      if (referenceExpression == null || !(referenceExpression.resolve() instanceof PsiVariable variable)) return null;
      for (int i = 1, operandsLength = operands.length; i < operandsLength; i++) {
        final PsiReferenceExpression reference2 = getReferenceFromImplicitNullCheckExpression(operands[i]);
        if (reference2 == null || !reference2.isReferenceTo(variable)) {
          return null;
        }
      }
      return referenceExpression;
    }
  }
}
