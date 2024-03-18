// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConstantValueVariableUseInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "constant.value.variable.use.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    return new ReplaceReferenceWithExpressionFix(expression.getText());
  }

  private static class ReplaceReferenceWithExpressionFix extends PsiUpdateModCommandQuickFix {
    private final String myText;

    ReplaceReferenceWithExpressionFix(String text) {
      myText = text;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiExpression expression)) {
        return;
      }
      PsiReplacementUtil.replaceExpression(expression, myText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantValueVariableUseVisitor();
  }

  private static class ConstantValueVariableUseVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression condition = statement.getCondition();
      final PsiStatement body = statement.getThenBranch();
      checkCondition(condition, body);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkLoop(statement);
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkLoop(statement);
    }

    private void checkLoop(PsiConditionalLoopStatement loop) {
      final PsiExpression condition = loop.getCondition();
      final PsiStatement body = loop.getBody();
      checkCondition(condition, body);
    }

    private boolean checkCondition(@Nullable PsiExpression condition,
                                   @Nullable PsiStatement body) {
      if (body == null) {
        return false;
      }
      if (!(condition instanceof PsiPolyadicExpression polyadicExpression)) {
        return false;
      }
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.ANDAND == tokenType) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          if (checkCondition(operand, body)) {
            return true;
          }
        }
        return false;
      }
      if (JavaTokenType.EQEQ != tokenType) {
        return false;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (operands.length != 2) {
        return false;
      }
      final PsiExpression lhs = operands[0];
      final PsiExpression rhs = operands[1];
      if (PsiUtil.isConstantExpression(lhs)) {
        return checkConstantValueVariableUse(rhs, lhs, body);
      }
      else if (PsiUtil.isConstantExpression(rhs)) {
        return checkConstantValueVariableUse(lhs, rhs, body);
      }
      return false;
    }

    private boolean checkConstantValueVariableUse(@Nullable PsiExpression expression,
                                                  @NotNull PsiExpression constantExpression,
                                                  @NotNull PsiElement body) {
      final PsiType constantType = constantExpression.getType();
      if (constantType == null) {
        return false;
      }
      if (PsiTypes.doubleType().equals(constantType)) {
        final Object result = ExpressionUtils.computeConstantExpression(constantExpression, false);
        if (Double.valueOf(0.0).equals(result) || Double.valueOf(-0.0).equals(result)) {
          return false;
        }
      }
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable variable)) {
        return false;
      }
      if (target instanceof PsiField) {
        return false;
      }
      final VariableReadVisitor visitor = new VariableReadVisitor(variable);
      body.accept(visitor);
      if (!visitor.isRead()) {
        return false;
      }
      final PsiReferenceExpression reference = visitor.getReference();
      final PsiType referenceType = reference.getType();
      if (referenceType == null) {
        return false;
      }
      if (!referenceType.isAssignableFrom(constantType)) {
        return false;
      }
      registerError(reference, constantExpression);
      return true;
    }
  }

  private static class VariableReadVisitor extends JavaRecursiveElementWalkingVisitor {

    @NotNull
    private final PsiVariable variable;
    private boolean read = false;
    private boolean stop = false;
    private PsiReferenceExpression reference = null;

    VariableReadVisitor(@NotNull PsiVariable variable) {
      this.variable = variable;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (read || stop) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (read || stop) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (variable.equals(target)) {
        if (PsiUtil.isAccessedForWriting(expression)) {
          stop = true;
          return;
        }
        if (PsiUtil.isAccessedForReading(expression)) {
          if (isInLoopCondition(expression)) {
            stop = true;
          }
          else {
            reference = expression;
            read = true;
          }
          return;
        }
      }
      super.visitReferenceExpression(expression);
    }

    private static boolean isInLoopCondition(PsiExpression expression) {
      final PsiStatement statement =
        PsiTreeUtil.getParentOfType(expression, PsiStatement.class, true, PsiMember.class, PsiLambdaExpression.class);
      return statement instanceof PsiLoopStatement;
    }

    public boolean isRead() {
      return read;
    }

    public PsiReferenceExpression getReference() {
      return reference;
    }
  }
}
