// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ControlTransferHandler {
  private @NotNull final DfaMemoryState state;
  private @NotNull final DataFlowInterpreter runner;
  private final @NotNull DfaControlTransferValue.TransferTarget target;
  private FList<DfaControlTransferValue.Trap> traps;
  private TypeConstraint throwableType;

  ControlTransferHandler(@NotNull DfaMemoryState state, @NotNull DataFlowInterpreter runner, @NotNull DfaControlTransferValue transferValue) {
    this.state = state;
    this.runner = runner;
    this.target = transferValue.getTarget();
    this.traps = transferValue.getTraps();
  }

  @NotNull DataFlowInterpreter getRunner() {
    return runner;
  }

  @NotNull DfaControlTransferValue.TransferTarget getTarget() {
    return target;
  }

  @NotNull DfaMemoryState getState() {
    return state;
  }

  FList<DfaControlTransferValue.Trap> getTraps() {
    return traps;
  }

  public @NotNull
  static List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                            @NotNull DataFlowInterpreter runner,
                                            @NotNull DfaControlTransferValue transferValue) {
    return new ControlTransferHandler(state, runner, transferValue).doDispatch();
  }

  List<DfaInstructionState> doDispatch() {
    DfaControlTransferValue.Trap head = traps.getHead();
    traps = traps.getTail() == null ? FList.emptyList() : traps.getTail();
    state.emptyStack();
    if (head != null) {
      return ((JvmTrap)head).dispatch(this);
    }
    return target.dispatch(state, runner);
  }

  private static List<TypeConstraint> allCaughtTypes(PsiParameter param) {
    PsiType type = param.getType();
    List<PsiType> types = type instanceof PsiDisjunctionType ? ((PsiDisjunctionType)type).getDisjunctions() : List.of(type);
    return ContainerUtil.map(types, TypeConstraints::instanceOf);
  }

  private DfaMemoryState stateForCatchClause(PsiParameter param, TypeConstraint constraint) {
    DfaMemoryState catchingCopy = state.createCopy();
    DfaVariableValue value = PlainDescriptor.createVariableValue(runner.getFactory(), param);
    catchingCopy.meetDfType(value, constraint.asDfType().meet(DfaNullability.NOT_NULL.asDfType()));
    return catchingCopy;
  }

  List<DfaInstructionState> processCatches(TypeConstraint thrownValue,
                                           Map<PsiCatchSection, ControlFlow.ControlFlowOffset> catches) {
    List<DfaInstructionState> result = new ArrayList<>();
    for (Map.Entry<PsiCatchSection, ControlFlow.ControlFlowOffset> entry : catches.entrySet()) {
      PsiCatchSection catchSection = entry.getKey();
      ControlFlow.ControlFlowOffset jumpOffset = entry.getValue();
      PsiParameter param = catchSection.getParameter();
      if (param == null) continue;
      if (throwableType == null) {
        throwableType = thrownValue;
      }

      for (TypeConstraint caughtType : allCaughtTypes(param)) {
        TypeConstraint caught = Objects.requireNonNull(throwableType).meet(caughtType);
        if (caught != TypeConstraints.BOTTOM) {
          result.add(new DfaInstructionState(runner.getInstruction(jumpOffset.getInstructionOffset()),
                                             stateForCatchClause(param, caught)));
        }

        TypeConstraint negated = caughtType.tryNegate();
        if (negated != null) {
          throwableType = throwableType == null ? null : throwableType.meet(negated);
          if (throwableType == TypeConstraints.BOTTOM) return result;
        }
      }
    }
    return ContainerUtil.concat(result, doDispatch());
  }
}
