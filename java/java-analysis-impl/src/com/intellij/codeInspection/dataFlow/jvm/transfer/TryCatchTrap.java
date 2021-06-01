// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.transfer;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue.Trap;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TryCatchTrap implements Trap {
  private final PsiTryStatement myStatement;
  private final LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset> myClauses;

  public TryCatchTrap(@NotNull PsiTryStatement tryStatement, @NotNull LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset> clauses) {
    myStatement = tryStatement;
    myClauses = clauses;
  }

  @Override
  public @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                     @NotNull DataFlowInterpreter interpreter,
                                                     DfaControlTransferValue.@NotNull TransferTarget target,
                                                     @NotNull FList<Trap> nextTraps) {
    if (!(target instanceof ExceptionTransfer)) {
      return DfaControlTransferValue.dispatch(state, interpreter, target, nextTraps);
    }
    state.emptyStack();
    TypeConstraint throwableType = ((ExceptionTransfer)target).getThrowable();
    List<DfaInstructionState> result = new ArrayList<>();
    for (Map.Entry<PsiCatchSection, ControlFlow.ControlFlowOffset> entry : myClauses.entrySet()) {
      PsiCatchSection catchSection = entry.getKey();
      ControlFlow.ControlFlowOffset jumpOffset = entry.getValue();
      PsiParameter param = catchSection.getParameter();
      if (param == null) continue;

      for (TypeConstraint caughtType : allCaughtTypes(param)) {
        TypeConstraint caught = Objects.requireNonNull(throwableType).meet(caughtType);
        if (caught != TypeConstraints.BOTTOM) {
          result.add(new DfaInstructionState(interpreter.getInstruction(jumpOffset.getInstructionOffset()),
                                             stateForCatchClause(state, interpreter, param, caught)));
        }

        TypeConstraint negated = caughtType.tryNegate();
        if (negated != null) {
          throwableType = throwableType.meet(negated);
          if (throwableType == TypeConstraints.BOTTOM) return result;
        }
      }
    }
    return ContainerUtil.concat(result, 
                                DfaControlTransferValue.dispatch(state, interpreter, new ExceptionTransfer(throwableType), nextTraps));
  }

  private static List<TypeConstraint> allCaughtTypes(PsiParameter param) {
    PsiType type = param.getType();
    List<PsiType> types = type instanceof PsiDisjunctionType ? ((PsiDisjunctionType)type).getDisjunctions() : List.of(type);
    return ContainerUtil.map(types, TypeConstraints::instanceOf);
  }

  private static DfaMemoryState stateForCatchClause(@NotNull DfaMemoryState state,
                                                    @NotNull DataFlowInterpreter interpreter,
                                                    @NotNull PsiParameter param,
                                                    @NotNull TypeConstraint constraint) {
    DfaMemoryState catchingCopy = state.createCopy();
    DfaVariableValue value = PlainDescriptor.createVariableValue(interpreter.getFactory(), param);
    catchingCopy.meetDfType(value, constraint.asDfType().meet(DfaNullability.NOT_NULL.asDfType()));
    return catchingCopy;
  }

  @Override
  public @NotNull PsiElement getAnchor() {
    return myStatement;
  }

  @Override
  public @NotNull Collection<@NotNull Integer> getPossibleTargets() {
    return ContainerUtil.map(myClauses.values(), ControlFlow.ControlFlowOffset::getInstructionOffset);
  }

  @Override
  public String toString() {
    return "TryCatch -> " + myClauses.values();
  }
}
