// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pops container and its index from the stack and ensures that the index (int value) is within the container length
 * Currently, used in Kotlin only
 */
public class EnsureIndexInBoundsInstruction extends Instruction {
  private final @NotNull IndexOutOfBoundsProblem myProblem;
  private final @Nullable DfaControlTransferValue myOutOfBoundsTransfer;

  public EnsureIndexInBoundsInstruction(@NotNull IndexOutOfBoundsProblem problem,
                                        @Nullable DfaControlTransferValue transfer) {
    myProblem = problem;
    myOutOfBoundsTransfer = transfer;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    DfaControlTransferValue newTransfer = myOutOfBoundsTransfer == null ? null : myOutOfBoundsTransfer.bindToFactory(factory);
    return new EnsureIndexInBoundsInstruction(myProblem, newTransfer);
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter,
                                      @NotNull DfaMemoryState stateBefore) {
    DfaValue index = stateBefore.pop();
    DfaValue container = stateBefore.pop();
    DfaInstructionState[] states = myProblem.processOutOfBounds(interpreter, stateBefore, index, container, myOutOfBoundsTransfer);
    if (states != null) return states;
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "ENSURE_INDEX " + myProblem.getLengthDescriptor();
  }
}
