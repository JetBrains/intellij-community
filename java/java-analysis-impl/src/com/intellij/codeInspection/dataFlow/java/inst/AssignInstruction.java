// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayStoreProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public class AssignInstruction extends ExpressionPushingInstruction {
  private final PsiExpression myRExpression;
  private final PsiExpression myLExpression;
  @Nullable private final DfaValue myAssignedValue;

  public AssignInstruction(PsiExpression rExpression, @Nullable DfaValue assignedValue) {
    this(getLeftHandOfAssignment(rExpression), rExpression, assignedValue);
  }

  public AssignInstruction(PsiExpression lExpression, PsiExpression rExpression, @Nullable DfaValue assignedValue) {
    super(getAnchor(rExpression));
    myLExpression = lExpression;
    myRExpression = rExpression;
    myAssignedValue = assignedValue;
  }

  @Nullable
  private static DfaAnchor getAnchor(PsiExpression rExpression) {
    if (rExpression == null) return null;
    PsiAssignmentExpression expression = tryCast(rExpression.getParent(), PsiAssignmentExpression.class);
    return expression == null ? null : new JavaExpressionAnchor(expression);
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myAssignedValue == null) return this;
    var instruction = new AssignInstruction(myLExpression, myRExpression, myAssignedValue.bindToFactory(factory));
    instruction.setIndex(getIndex());
    return instruction;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaSource = stateBefore.pop();
    DfaValue dfaDest = stateBefore.pop();

    if (!(dfaDest instanceof DfaVariableValue) && myAssignedValue != null) {
      // It's possible that dfaDest on the stack is cleared to DfaTypeValue due to variable flush
      // (e.g. during StateMerger#mergeByFacts), so we try to restore the original destination.
      dfaDest = myAssignedValue;
    }
    interpreter.getListener().beforeAssignment(dfaSource, dfaDest, stateBefore, getDfaAnchor());
    if (dfaSource == dfaDest) {
      stateBefore.push(dfaDest);
      this.flushArrayOnUnknownAssignment(interpreter.getFactory(), dfaDest, stateBefore);
      return nextStates(interpreter, stateBefore);
    }
    if (!(dfaDest instanceof DfaVariableValue &&
          ((DfaVariableValue)dfaDest).getPsiVariable() instanceof PsiLocalVariable &&
          dfaSource instanceof DfaVariableValue &&
          (ControlFlow.isTempVariable((DfaVariableValue)dfaSource) ||
           ((DfaVariableValue)dfaSource).getDescriptor().isCall()))) {
      JavaDfaHelpers.dropLocality(dfaSource, stateBefore);
    }

    PsiExpression lValue = PsiUtil.skipParenthesizedExprDown(getLExpression());
    PsiExpression rValue = getRExpression();
    if (lValue instanceof PsiArrayAccessExpression) {
      checkArrayElementAssignability(interpreter, stateBefore, dfaSource, dfaDest, lValue, rValue);
    }

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;

      PsiElement psi = var.getPsiVariable();
      if (dfaSource instanceof DfaTypeValue &&
          ((psi instanceof PsiField && ((PsiField)psi).hasModifierProperty(PsiModifier.STATIC)) ||
           (var.getQualifier() != null && !DfReferenceType.isLocal(stateBefore.getDfType(var.getQualifier()))))) {
        DfType dfType = dfaSource.getDfType();
        if (dfType instanceof DfReferenceType) {
          dfaSource = dfaSource.getFactory().fromDfType(((DfReferenceType)dfType).dropLocality());
        }
      }
      if (!(psi instanceof PsiField) || !((PsiField)psi).hasModifierProperty(PsiModifier.VOLATILE)) {
        stateBefore.setVarValue(var, dfaSource);
      }
      if (DfaNullability.fromDfType(var.getInherentType()) == DfaNullability.NULLABLE &&
          DfaNullability.fromDfType(stateBefore.getDfType(var)) == DfaNullability.UNKNOWN && isVariableInitializer()) {
        stateBefore.meetDfType(var, DfaNullability.NULLABLE.asDfType());
      }
    }

    pushResult(interpreter, stateBefore, dfaDest);
    this.flushArrayOnUnknownAssignment(interpreter.getFactory(), dfaDest, stateBefore);

    return nextStates(interpreter, stateBefore);
  }

  @Nullable
  public PsiExpression getRExpression() {
    return myRExpression;
  }

  @Nullable
  public PsiExpression getLExpression() {
    return myLExpression;
  }

  @Nullable
  public DfaValue getAssignedValue() {
    return myAssignedValue;
  }

  private boolean isVariableInitializer() {
    return myRExpression != null && myRExpression.getParent() instanceof PsiVariable;
  }

  @Contract("null -> null")
  @Nullable
  private static PsiExpression getLeftHandOfAssignment(PsiExpression rExpression) {
    if(rExpression == null) return null;
    if(rExpression.getParent() instanceof PsiAssignmentExpression) {
      return ((PsiAssignmentExpression)rExpression.getParent()).getLExpression();
    }
    return null;
  }

  private static void checkArrayElementAssignability(@NotNull DataFlowInterpreter runner,
                                                     @NotNull DfaMemoryState memState,
                                                     @NotNull DfaValue dfaSource,
                                                     @NotNull DfaValue dfaDest,
                                                     @NotNull PsiExpression lValue,
                                                     @Nullable PsiExpression rValue) {
    if (rValue == null) return;
    PsiType rCodeType = rValue.getType();
    PsiType lCodeType = lValue.getType();
    // If types known from source are not convertible, a compilation error is displayed, additional warning is unnecessary
    if (rCodeType == null || lCodeType == null || !TypeConversionUtil.areTypesConvertible(rCodeType, lCodeType)) return;
    if (!(dfaDest instanceof DfaVariableValue)) return;
    DfaVariableValue qualifier = ((DfaVariableValue)dfaDest).getQualifier();
    if (qualifier == null) return;
    DfType toType = TypeConstraint.fromDfType(memState.getDfType(qualifier)).getArrayComponentType();
    if (toType == DfType.BOTTOM) return;
    DfType fromType = memState.getDfType(dfaSource);
    DfType meet = fromType.meet(toType);
    Project project = lValue.getProject();
    PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(rValue, PsiAssignmentExpression.class);
    PsiType psiFromType = TypeConstraint.fromDfType(fromType).getPsiType(project);
    PsiType psiToType = TypeConstraint.fromDfType(toType).getPsiType(project);
    if (assignmentExpression == null || psiFromType == null || psiToType == null) return;
    runner.getListener().onCondition(new ArrayStoreProblem(assignmentExpression, psiFromType, psiToType), dfaSource,
                                        meet == DfType.BOTTOM ? ThreeState.YES : ThreeState.UNSURE, memState);
  }

  @Override
  public String toString() {
    return "ASSIGN";
  }

  private void flushArrayOnUnknownAssignment(@NotNull DfaValueFactory factory,
                                             @NotNull DfaValue dest,
                                             @NotNull DfaMemoryState memState) {
    if (dest instanceof DfaVariableValue) return;
    PsiArrayAccessExpression arrayAccess =
      tryCast(PsiUtil.skipParenthesizedExprDown(myLExpression), PsiArrayAccessExpression.class);
    if (arrayAccess != null) {
      PsiExpression array = arrayAccess.getArrayExpression();
      DfaValue value = JavaDfaValueFactory.getExpressionDfaValue(factory, array);
      if (value instanceof DfaVariableValue) {
        for (DfaVariableValue qualified : ((DfaVariableValue)value).getDependentVariables().toArray(new DfaVariableValue[0])) {
          if (qualified.isFlushableByCalls()) {
            memState.flushVariable(qualified);
          }
        }
      }
    }
  }
}
