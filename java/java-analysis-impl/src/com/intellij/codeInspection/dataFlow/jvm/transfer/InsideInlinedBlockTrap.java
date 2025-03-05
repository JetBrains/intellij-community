// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.jvm.transfer;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class InsideInlinedBlockTrap implements DfaControlTransferValue.Trap {
  private final @NotNull PsiElement myInlinedBlock;

  public InsideInlinedBlockTrap(@NotNull PsiElement block) {
    myInlinedBlock = block; 
  }

  @Override
  public @Unmodifiable @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                                   @NotNull DataFlowInterpreter interpreter,
                                                                   DfaControlTransferValue.@NotNull TransferTarget target,
                                                                   @NotNull FList<DfaControlTransferValue.Trap> nextTraps) {
    DfaValue value = state.pop();
    if (!(value instanceof DfaControlTransferValue) ||
        ((DfaControlTransferValue)value).getTarget() != DfaControlTransferValue.RETURN_TRANSFER) {
      throw new IllegalStateException("Expected control transfer on stack; got " + value);
    }
    return DfaControlTransferValue.dispatch(state, interpreter, target, nextTraps);
  }

  @Override
  public @NotNull PsiElement getAnchor() {
    return myInlinedBlock;
  }

  @Override
  public String toString() {
    return "InlinedBlock";
  }
}
