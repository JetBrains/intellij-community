// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public final class OverflowingLoopIndexInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(@NotNull PsiForStatement statement) {
        PsiExpressionStatement updateStatement = tryCast(statement.getUpdate(), PsiExpressionStatement.class);
        if (updateStatement == null) return;
        PsiExpression updateExpression = updateStatement.getExpression();

        PsiLocalVariable indexVariable = findIndexVariable(updateExpression);
        if (indexVariable == null) return;
        PsiType type = indexVariable.getType();
        if (!type.equals(PsiTypes.intType()) && !type.equals(PsiTypes.longType())) return;

        ConditionType conditionType = getConditionType(statement.getCondition(), indexVariable);
        if (conditionType == ConditionType.Unknown) return;

        if (!notChangesMonotony(conditionType, updateExpression)) return;
        if (indexUpdatedInBody(statement, indexVariable, conditionType)) return;
        holder.registerProblem(statement.getFirstChild(), JavaBundle.message("inspection.overflowing.loop.index.inspection.description"));
      }
    };
  }

  @Nullable
  private static PsiLocalVariable findIndexVariable(PsiExpression updateExpression) {
    if (updateExpression instanceof PsiUnaryExpression unaryExpression) {
      return ExpressionUtils.resolveLocalVariable(unaryExpression.getOperand());
    }
    if (updateExpression instanceof PsiAssignmentExpression assignmentExpression) {
      return ExpressionUtils.resolveLocalVariable(assignmentExpression.getLExpression());
    }
    return null;
  }

  private enum ConditionType {
    Unknown,
    VarGreater,
    VarLower;

    ConditionType inverted() {
      return switch (this) {
        case VarGreater -> VarLower;
        case VarLower -> VarGreater;
        default -> Unknown;
      };
    }
  }

  private static ConditionType getConditionType(@Nullable PsiExpression condition, @NotNull PsiVariable variable) {
    PsiBinaryExpression binaryExpression = tryCast(condition, PsiBinaryExpression.class);
    if (binaryExpression == null) return ConditionType.Unknown;
    IElementType tokenType = binaryExpression.getOperationTokenType();
    ConditionType type = ConditionType.Unknown;
    if (tokenType == JavaTokenType.GT || tokenType == JavaTokenType.GE) {
      type = ConditionType.VarGreater;
    } else if (tokenType == JavaTokenType.LT || tokenType == JavaTokenType.LE) {
      type = ConditionType.VarLower;
    }
    if (type != ConditionType.Unknown) {
      PsiExpression lOperand = binaryExpression.getLOperand();
      if (!ExpressionUtils.isReferenceTo(lOperand, variable)) {
        if (ExpressionUtils.isReferenceTo(binaryExpression.getROperand(), variable)) {
          type = type.inverted();
        } else {
          type = ConditionType.Unknown;
        }
      }
    }
    return type;
  }

  private static boolean indexUpdatedInBody(PsiForStatement statement, PsiLocalVariable indexVariable, ConditionType conditionType) {
    PsiStatement body = statement.getBody();
    if (body == null) return true;
    return !StreamEx.ofTree((PsiElement)body, element -> StreamEx.of(element.getChildren()))
      .select(PsiReferenceExpression.class)
      .filter(expression -> expression.isReferenceTo(indexVariable))
      .allMatch(expression -> {
        PsiElement parent = expression.getParent();
        if ((parent instanceof PsiUnaryExpression psiUnaryExpression &&
             ExpressionUtils.isReferenceTo(psiUnaryExpression.getOperand(), indexVariable)) ||
            (parent instanceof PsiAssignmentExpression assignmentExpression &&
             ExpressionUtils.isReferenceTo(assignmentExpression.getLExpression(), indexVariable))) {
          return notChangesMonotony(conditionType, tryCast(parent, PsiExpression.class));
        }
        return true;
      });
  }

  private static boolean notChangesMonotony(ConditionType conditionType,
                                            @Nullable PsiExpression expression) {
    PsiUnaryExpression updateUnary = tryCast(expression, PsiUnaryExpression.class);
    if (updateUnary != null) {
      IElementType tokenType = updateUnary.getOperationTokenType();
      if (conditionType == ConditionType.VarGreater) {
        return tokenType == JavaTokenType.PLUSPLUS;
      } else {
        return tokenType == JavaTokenType.MINUSMINUS;
      }
    }
    PsiAssignmentExpression assignment = tryCast(expression, PsiAssignmentExpression.class);
    if (assignment != null) {
      Object rightPart = ExpressionUtils.computeConstantExpression(assignment.getRExpression());
      Number number = tryCast(rightPart, Number.class);
      if (number == null) return false;
      boolean negative = number.longValue() < 0;
      IElementType op = assignment.getOperationTokenType();
      if (conditionType == ConditionType.VarGreater) {
        return (op == JavaTokenType.PLUSEQ && !negative) || (op == JavaTokenType.MINUSEQ && negative);
      } else {
        return (op == JavaTokenType.MINUSEQ && !negative) || (op == JavaTokenType.PLUSEQ && negative);
      }
    }
    return false;
  }
}
