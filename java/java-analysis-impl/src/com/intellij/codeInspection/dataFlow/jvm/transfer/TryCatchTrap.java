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
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TryCatchTrap implements Trap {
  private final PsiElement myStatement;
  private final LinkedHashMap<CatchClauseDescriptor, ? extends ControlFlow.ControlFlowOffset> myClauses;

  public TryCatchTrap(@NotNull PsiElement tryStatement,
                      @NotNull LinkedHashMap<CatchClauseDescriptor, ? extends ControlFlow.ControlFlowOffset> clauses) {
    myStatement = tryStatement;
    myClauses = clauses;
  }

  public interface CatchClauseDescriptor {
    @NotNull List<TypeConstraint> constraints();
    @Nullable VariableDescriptor parameter();
  }

  public static class JavaCatchClauseDescriptor implements CatchClauseDescriptor {
    private final PsiCatchSection mySection;

    public JavaCatchClauseDescriptor(PsiCatchSection section) {
      mySection = section;
    }

    @Override
    public @NotNull List<TypeConstraint> constraints() {
      PsiParameter parameter = mySection.getParameter();
      if (parameter == null) return Collections.emptyList();
      PsiType type = parameter.getType();
      List<PsiType> types = type instanceof PsiDisjunctionType ? ((PsiDisjunctionType)type).getDisjunctions() : List.of(type);
      return ContainerUtil.map(types, TypeConstraints::instanceOf);
    }

    @Override
    public @Nullable VariableDescriptor parameter() {
      PsiParameter parameter = mySection.getParameter();
      return parameter == null ? null : new PlainDescriptor(parameter);
    }
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
    for (var entry : myClauses.entrySet()) {
      ControlFlow.ControlFlowOffset jumpOffset = entry.getValue();
      CatchClauseDescriptor catchSection = entry.getKey();

      for (TypeConstraint caughtType : catchSection.constraints()) {
        TypeConstraint caught = Objects.requireNonNull(throwableType).meet(caughtType);
        if (caught != TypeConstraints.BOTTOM) {
          result.add(new DfaInstructionState(interpreter.getInstruction(jumpOffset.getInstructionOffset()),
                                             stateForCatchClause(state, interpreter, catchSection.parameter(), caught)));
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

  private static DfaMemoryState stateForCatchClause(@NotNull DfaMemoryState state,
                                                    @NotNull DataFlowInterpreter interpreter,
                                                    @Nullable VariableDescriptor param,
                                                    @NotNull TypeConstraint constraint) {
    DfaMemoryState catchingCopy = state.createCopy();
    if (param != null) {
      DfaVariableValue value = interpreter.getFactory().getVarFactory().createVariableValue(param);
      catchingCopy.meetDfType(value, constraint.asDfType().meet(DfaNullability.NOT_NULL.asDfType()));
    }
    return catchingCopy;
  }

  @Override
  public @NotNull PsiElement getAnchor() {
    return myStatement;
  }

  @Override
  public int @NotNull [] getPossibleTargets() {
    Collection<? extends ControlFlow.ControlFlowOffset> values = myClauses.values();
    int[] result = new int[values.size()];
    int i = 0;
    for (ControlFlow.ControlFlowOffset value : values) {
      result[i++] = value.getInstructionOffset();
    }
    return result;
  }

  @Override
  public String toString() {
    return "TryCatch -> " + myClauses.values();
  }
}
