// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Instruction which performs complex control transfer (handling exception; processing finally blocks; exiting inlined lambda, etc.)
 */
public class ControlTransferInstruction extends Instruction {
  private final @NotNull DfaControlTransferValue myTransfer;

  public ControlTransferInstruction(@NotNull DfaControlTransferValue transfer) {
    this(transfer, true);
  }

  public ControlTransferInstruction(@NotNull DfaControlTransferValue transfer, boolean linkTraps) {
    myTransfer = transfer;
    if (linkTraps) {
      transfer.getTraps().forEach(trap -> trap.link(transfer));
    }
  }

  public @NotNull DfaControlTransferValue getTransfer() {
    return myTransfer;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new ControlTransferInstruction(myTransfer.bindToFactory(factory), false);
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter,
                                      @NotNull DfaMemoryState stateBefore) {
    return myTransfer.dispatch(stateBefore, interpreter).toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  /**
   * Returns list of possible target instruction indices
   */
  @Override
  public int @NotNull [] getSuccessorIndexes() {
    return myTransfer.getPossibleTargetIndices();
  }

  @Override
  public boolean isLinear() {
    return false;
  }

  @Override
  public String toString() {
    int[] indexes = getSuccessorIndexes();
    return "TRANSFER " + myTransfer + (indexes.length == 0 ? "" : " [targets: " + Arrays.toString(indexes) + "]");
  }
}
