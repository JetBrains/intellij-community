// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.jvm.transfer;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class TryCatchAllTrap implements DfaControlTransferValue.Trap {
  private final @NotNull PsiElement myAnchor;
  private final ControlFlow.@NotNull ControlFlowOffset myTarget;

  public TryCatchAllTrap(@NotNull PsiElement anchor, ControlFlow.@NotNull ControlFlowOffset target) {
    myAnchor = anchor;
    myTarget = target;
  }

  @Override
  public @Unmodifiable @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                                   @NotNull DataFlowInterpreter interpreter,
                                                                   DfaControlTransferValue.@NotNull TransferTarget target,
                                                                   @NotNull FList<DfaControlTransferValue.Trap> nextTraps) {
    if (!(target instanceof ExceptionTransfer)) {
      return DfaControlTransferValue.dispatch(state, interpreter, target, nextTraps);
    }
    state.emptyStack();
    return List.of(new DfaInstructionState(interpreter.getInstruction(myTarget.getInstructionOffset()), state));
  }

  @Override
  public @NotNull PsiElement getAnchor() {
    return myAnchor;
  }

  @Override
  public String toString() {
    return "TryCatchAll -> " + myTarget;
  }
}
