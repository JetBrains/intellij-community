// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.transfer;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class InstructionTransfer implements DfaControlTransferValue.TransferTarget {
  private final ControlFlow.@NotNull ControlFlowOffset myOffset;
  private final @NotNull List<VariableDescriptor> myVarsToFlush;

  public InstructionTransfer(@NotNull ControlFlow.ControlFlowOffset offset, @NotNull List<VariableDescriptor> flush) {
    myOffset = offset;
    myVarsToFlush = flush;
  }

  @Override
  public int @NotNull [] getPossibleTargets() {
    return new int[]{myOffset.getInstructionOffset()};
  }
  
  @Override
  public @NotNull List<@NotNull DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                              @NotNull DataFlowInterpreter interpreter) {
    state.flushVariables(var -> myVarsToFlush.contains(var.getDescriptor()));
    return List.of(new DfaInstructionState(interpreter.getInstruction(myOffset.getInstructionOffset()), state));
  }

  @Override
  public String toString() {
    return "-> " + myOffset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InstructionTransfer transfer = (InstructionTransfer)o;
    return myOffset.equals(transfer.myOffset) && myVarsToFlush.equals(transfer.myVarsToFlush);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myOffset, myVarsToFlush);
  }
}
