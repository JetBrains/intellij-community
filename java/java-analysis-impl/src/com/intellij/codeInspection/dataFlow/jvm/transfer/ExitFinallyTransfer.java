// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.transfer;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

// ExitFinallyTransfer formally depends on enterFinally that has backLinks which are instructions bound to the DfaValueFactory
// however, we actually use only instruction offsets from there, and binding to another factory does not change the offsets.
public class ExitFinallyTransfer implements DfaControlTransferValue.TransferTarget {
  private final @NotNull EnterFinallyTrap myEnterFinally;

  public ExitFinallyTransfer(@NotNull EnterFinallyTrap enterFinally) { 
    myEnterFinally = enterFinally; 
  }

  @Override
  public int @NotNull [] getPossibleTargets() {
    return StreamEx.of(myEnterFinally.backLinks()).flatMapToInt(link -> IntStreamEx.of(link.getPossibleTargetIndices()))
      .filter(index -> index != myEnterFinally.getJumpOffset()).toArray();
  }

  @Override
  public @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                     @NotNull DataFlowInterpreter interpreter) {
    return ((DfaControlTransferValue)state.pop()).dispatch(state, interpreter);
  }

  @Override
  public String toString() {
    return "ExitFinally";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExitFinallyTransfer transfer = (ExitFinallyTransfer)o;
    return myEnterFinally.equals(transfer.myEnterFinally);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myEnterFinally);
  }
}
