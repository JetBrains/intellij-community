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

public class OverflowingLoopIndexInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(PsiForStatement statement) {
        PsiStatement initialization = statement.getInitialization();
        PsiDeclarationStatement declaration = tryCast(initialization, PsiDeclarationStatement.class);
        if (declaration == null) return;
        PsiElement[] declaredElements = declaration.getDeclaredElements();
        if (declaredElements.length != 1) return;
        PsiLocalVariable indexVariable = tryCast(declaredElements[0], PsiLocalVariable.class);
        if (indexVariable == null) return;
        PsiType type = indexVariable.getType();
        if (!type.equals(PsiType.INT) && !type.equals(PsiType.LONG)) return;

        ConditionType conditionType = getConditionType(statement.getCondition(), indexVariable);
        if (conditionType == ConditionType.Unknown) return;


        if(!isMonotonousUpdate(statement.getUpdate(), conditionType, indexVariable)) return;
        if (indexUpdatedInBody(statement, indexVariable, conditionType)) return;
        holder.registerProblem(statement.getFirstChild(), JavaBundle.message("inspection.overflowing.loop.index.inspection.description"));
      }
    };
  }

  private enum ConditionType {
    Unknown,
    VarGreater,
    VarLower;

    ConditionType inverted() {
      switch (this) {
        case VarGreater:
          return VarLower;
        case VarLower:
          return VarGreater;
        default:
          return Unknown;
      }
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
        if (parent instanceof PsiUnaryExpression ||
            (parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getRExpression() != expression)) {
          return notChangesMonotony(conditionType, indexVariable, tryCast(parent, PsiExpression.class));
        }
        return true;
      });
    //ControlFlow flow;
    //try {
    //  ControlFlowFactory factory = ControlFlowFactory.getInstance(statement.getProject());
    //  flow = factory.getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    //}
    //catch (AnalysisCanceledException e) {
    //  return true;
    //}
    //Collection<PsiVariable> writtenVariables =
    //  ControlFlowUtil.getWrittenVariables(flow, flow.getStartOffset(body), flow.getEndOffset(body), true);
    //if (writtenVariables.contains(indexVariable)) return true;
    //return false;
  }

  private static boolean isMonotonousUpdate(@Nullable PsiStatement statement, ConditionType conditionType, PsiVariable variable) {
    PsiExpressionStatement expressionStatement = tryCast(statement, PsiExpressionStatement.class);
    if (expressionStatement == null) return false;
    PsiExpression expression = expressionStatement.getExpression();
    return notChangesMonotony(conditionType, variable, expression);
  }

  private static boolean notChangesMonotony(ConditionType conditionType,
                                            PsiVariable variable,
                                            @Nullable PsiExpression expression) {
    PsiUnaryExpression updateUnary = tryCast(expression, PsiUnaryExpression.class);
    if (updateUnary != null) {
      IElementType tokenType = updateUnary.getOperationTokenType();
      if (!ExpressionUtils.isReferenceTo(updateUnary.getOperand(), variable)) return false;
      if (conditionType == ConditionType.VarGreater) {
        return tokenType == JavaTokenType.PLUSPLUS;
      } else {
        return tokenType == JavaTokenType.MINUSMINUS;
      }
    }
    PsiAssignmentExpression assignment = tryCast(expression, PsiAssignmentExpression.class);
    if (assignment != null) {
      if (!ExpressionUtils.isReferenceTo(assignment.getLExpression(), variable)) return false;
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
