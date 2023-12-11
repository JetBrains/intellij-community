// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class LoopConditionNotUpdatedInsideLoopInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignorePossibleNonLocalChanges = true;

  // Preserved for serialization compatibility
  @SuppressWarnings("unused")
  public boolean ignoreIterators = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean entireCondition = ((Boolean)infos[0]).booleanValue();
    if (entireCondition) {
      return InspectionGadgetsBundle.message("loop.condition.not.updated.inside.loop.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("loop.variable.not.updated.inside.loop.problem.descriptor");
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "ignorePossibleNonLocalChanges");
    writeBooleanOption(node, "ignorePossibleNonLocalChanges", true);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignorePossibleNonLocalChanges", InspectionGadgetsBundle.message("loop.variable.not.updated.inside.loop.option.nonlocal")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoopConditionNotUpdatedInsideLoopVisitor();
  }

  private class LoopConditionNotUpdatedInsideLoopVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      check(statement);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      check(statement);
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      check(statement);
    }

    private void check(@NotNull PsiConditionalLoopStatement statement) {
      final PsiExpression condition = statement.getCondition();
      final List<PsiExpression> notUpdated = new SmartList<>();
      PsiStatement body = statement.getBody();
      if (body == null || condition == null || SideEffectChecker.mayHaveSideEffects(condition)) return;
      if (ignorePossibleNonLocalChanges && !ExpressionUtils.isLocallyDefinedExpression(condition)) {
        if (SideEffectChecker.mayHaveNonLocalSideEffects(body)) return;
        if (statement instanceof PsiForStatement) {
          PsiStatement update = ((PsiForStatement)statement).getUpdate();
          if (update != null && SideEffectChecker.mayHaveNonLocalSideEffects(update)) return;
        }
      }
      if (isConditionNotUpdated(condition, statement, notUpdated)) {
        if (!ControlFlowUtils.statementMayCompleteNormally(body) && !ControlFlowUtils.statementIsContinueTarget(statement)) {
          // Such loop is reported by LoopStatementsThatDontLoopInspection, so no need to report
          // "Loop condition is not updated" if it's checked only once anyways.
          // Sometimes people write while(flag) {...; break;}
          // instead of if(flag) {...} just to be able to use break inside (though the 'if' could be labeled instead)
          return;
        }
        if (notUpdated.isEmpty()) {
          // condition involves only final variables and/or constants,
          // flag the whole condition
          if (!BoolUtils.isBooleanLiteral(condition)) {
            registerError(condition, Boolean.TRUE);
          }
        }
        else {
          for (PsiExpression expression : notUpdated) {
            registerError(expression, Boolean.FALSE);
          }
        }
      }
    }

    private static boolean isConditionNotUpdated(@Nullable PsiExpression condition,
                                                 @NotNull PsiStatement context,
                                                 List<? super PsiExpression> notUpdated) {
      if (condition == null) {
        return false;
      }
      if (PsiUtil.isConstantExpression(condition) || ExpressionUtils.isNullLiteral(condition)) {
        return true;
      }
      if (condition instanceof PsiInstanceOfExpression instanceOfExpression) {
        final PsiExpression operand = instanceOfExpression.getOperand();
        return isConditionNotUpdated(operand, context, notUpdated);
      }
      else if (condition instanceof PsiParenthesizedExpression) {
        // catch stuff like "while ((x)) { ... }"
        final PsiExpression expression =
          ((PsiParenthesizedExpression)condition).getExpression();
        return isConditionNotUpdated(expression, context, notUpdated);
      }
      else if (condition instanceof PsiPolyadicExpression polyadicExpression) {
        // while (value != x) { ... }
        // while (value != (x + y)) { ... }
        // while (b1 && b2) { ... }
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          if (!isConditionNotUpdated(operand, context, notUpdated)) {
            return false;
          }
        }
        return true;
      }
      else if (condition instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement element = referenceExpression.resolve();
        if (element instanceof PsiField field) {
          final PsiType type = field.getType();
          if (field.hasModifierProperty(PsiModifier.FINAL) &&
              type.getArrayDimensions() == 0) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
              return true;
            }
            final PsiExpression qualifier =
              referenceExpression.getQualifierExpression();
            if (qualifier == null) {
              return true;
            }
            else if (isConditionNotUpdated(qualifier, context,
                                           notUpdated)) {
              return true;
            }
          }
        }
        else if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
          final PsiVariable variable = (PsiVariable)element;
          boolean isFinal = variable.hasModifierProperty(PsiModifier.FINAL);
          final PsiType type = variable.getType();
          boolean arrayUpdated = type instanceof PsiArrayType && VariableAccessUtils.arrayContentsAreAssigned(variable, context);
          if ((isFinal || !VariableAccessUtils.variableIsAssigned(variable, context)) && !arrayUpdated) {
            if (!isFinal) {
              notUpdated.add(referenceExpression);
            }
            return true;
          }
        }
      }
      else if (condition instanceof PsiPrefixExpression prefixExpression) {
        if (!PsiUtil.isIncrementDecrementOperation(prefixExpression)) {
          final PsiExpression operand = prefixExpression.getOperand();
          return isConditionNotUpdated(operand, context, notUpdated);
        }
      }
      else if (condition instanceof PsiArrayAccessExpression accessExpression) {
        // Actually the contents of the array could change nevertheless
        // if it is accessed through a different reference like this:
        //   int[] local_ints = new int[]{1, 2};
        //   int[] other_ints = local_ints;
        //   while (local_ints[0] > 0) { other_ints[0]--; }
        //
        // Keep this check?
        final PsiExpression indexExpression = accessExpression.getIndexExpression();
        return isConditionNotUpdated(indexExpression, context, notUpdated)
               && isConditionNotUpdated(accessExpression.getArrayExpression(), context, notUpdated);
      }
      else if (condition instanceof PsiConditionalExpression conditionalExpression) {
        final PsiExpression thenExpression =
          conditionalExpression.getThenExpression();
        final PsiExpression elseExpression =
          conditionalExpression.getElseExpression();
        if (thenExpression == null || elseExpression == null) {
          return false;
        }
        return isConditionNotUpdated(conditionalExpression.getCondition(), context, notUpdated)
               && isConditionNotUpdated(thenExpression, context, notUpdated)
               && isConditionNotUpdated(elseExpression, context, notUpdated);
      }
      else if (condition instanceof PsiMethodCallExpression) {
        PsiExpression qualifier = ((PsiMethodCallExpression)condition).getMethodExpression().getQualifierExpression();
        if (!isConditionNotUpdated(qualifier, context, notUpdated)) return false;
        for (PsiExpression arg : ((PsiMethodCallExpression)condition).getArgumentList().getExpressions()) {
          if (!isConditionNotUpdated(arg, context, notUpdated)) return false;
        }
        return true;
      }
      else if (condition instanceof PsiTypeCastExpression) {
        return isConditionNotUpdated(((PsiTypeCastExpression)condition).getOperand(), context, notUpdated);
      }
      else if (condition instanceof PsiThisExpression || condition instanceof PsiClassObjectAccessExpression) {
        return true;
      }
      return false;
    }
  }
}