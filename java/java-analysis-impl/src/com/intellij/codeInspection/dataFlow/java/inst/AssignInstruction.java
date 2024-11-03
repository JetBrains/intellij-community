// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
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
import com.intellij.psi.*;
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
    return new AssignInstruction(myLExpression, myRExpression, myAssignedValue.bindToFactory(factory));
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
      return nextStates(interpreter, stateBefore);
    }
    if (!(dfaDest instanceof DfaVariableValue destVar && destVar.getPsiVariable() instanceof PsiLocalVariable &&
          dfaSource instanceof DfaVariableValue sourceVar &&
          (ControlFlow.isTempVariable(sourceVar) || (sourceVar).getDescriptor().isCall()))) {
      JavaDfaHelpers.dropLocality(dfaSource, stateBefore);
    }

    if (dfaDest instanceof DfaVariableValue var) {
      PsiElement psi = var.getPsiVariable();
      if (dfaSource instanceof DfaTypeValue &&
          ((psi instanceof PsiField field && field.hasModifierProperty(PsiModifier.STATIC)) ||
           (var.getQualifier() != null && !stateBefore.getDfType(var.getQualifier()).isLocal()))) {
        DfType dfType = dfaSource.getDfType();
        if (dfType instanceof DfReferenceType refType) {
          dfaSource = dfaSource.getFactory().fromDfType(refType.dropLocality());
        }
      }
      stateBefore.setVarValue(var, dfaSource);
      if (DfaNullability.fromDfType(var.getInherentType()) == DfaNullability.NULLABLE &&
          DfaNullability.fromDfType(stateBefore.getDfType(var)) == DfaNullability.UNKNOWN && isVariableInitializer()) {
        stateBefore.meetDfType(var, DfaNullability.NULLABLE.asDfType());
      }
    }

    pushResult(interpreter, stateBefore, dfaDest instanceof DfaVariableValue ? dfaDest : dfaSource);
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

  @Override
  public String toString() {
    return "ASSIGN";
  }
}
