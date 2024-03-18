// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AssertWithSideEffectsInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assert.with.side.effects.problem.descriptor", infos[0]);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertWithSideEffectsVisitor();
  }

  private static class AssertWithSideEffectsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
      super.visitAssertStatement(statement);
      final PsiExpression condition = statement.getAssertCondition();
      if (condition == null) {
        return;
      }
      final SideEffectVisitor visitor = new SideEffectVisitor();
      condition.accept(visitor);
      String description = visitor.getSideEffectDescription();
      if (description == null) {
        return;
      }
      registerStatementError(statement, description);
    }
  }

  private static class SideEffectVisitor extends JavaRecursiveElementWalkingVisitor {
    private @Nls String sideEffectDescription;

    private @Nls String getSideEffectDescription() {
      return sideEffectDescription;
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      sideEffectDescription = expression.getLExpression().getText() + " " + expression.getOperationSign().getText() + " ...";
      stopWalking();
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      sideEffectDescription = getCallSideEffectDescription(expression);
      if (sideEffectDescription != null) {
        stopWalking();
      }
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        sideEffectDescription = expression.getText();
        stopWalking();
      } else {
        super.visitUnaryExpression(expression);
      }
    }
  }

  private static @Nullable @Nls String getCallSideEffectDescription(PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    if (JavaMethodContractUtil.isPure(method)) return null;
    MutationSignature signature = MutationSignature.fromMethod(method);
    if (signature.mutatesAnything()) {
      PsiExpression expression =
        signature.mutatedExpressions(call).filter(expr -> !ExpressionUtils.isNewObject(expr)).findFirst().orElse(null);
      if (expression != null) {
        return InspectionGadgetsBundle.message("assert.with.side.effects.call.mutates.expression", method.getName(), expression.getText());
      }
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return null;
    }
    final MethodSideEffectVisitor visitor = new MethodSideEffectVisitor();
    body.accept(visitor);
    String description = visitor.getMutatedField();
    if (description != null) {
      return InspectionGadgetsBundle.message("assert.with.side.effects.call.mutates.field", method.getName(), description);
    }
    return null;
  }

  private static class MethodSideEffectVisitor extends JavaRecursiveElementWalkingVisitor {
    private String mutatedField;

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      checkExpression(expression.getLExpression());
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        checkExpression(expression.getOperand());
      }
      super.visitUnaryExpression(expression);
    }

    private void checkExpression(PsiExpression operand) {
      operand = PsiUtil.skipParenthesizedExprDown(operand);
      if (!(operand instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiField) {
        mutatedField = ((PsiField)target).getName();
        stopWalking();
      }
    }

    private String getMutatedField() {
      return mutatedField;
    }
  }
}