// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.lang.ir.inst.Instruction;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DfaInstructionState implements Comparable<DfaInstructionState> {
  public static final DfaInstructionState[] EMPTY_ARRAY = new DfaInstructionState[0];
  private final DfaMemoryState myBeforeMemoryState;
  private final Instruction myInstruction;

  public DfaInstructionState(@NotNull Instruction myInstruction, @NotNull DfaMemoryState myBeforeMemoryState) {
    this.myBeforeMemoryState = myBeforeMemoryState;
    this.myInstruction = myInstruction;
  }

  @NotNull
  public Instruction getInstruction() {
    return myInstruction;
  }
  
  public DfaInstructionState[] nextStates(DataFlowRunner runner) {
    return myInstruction.nextStates(runner, myBeforeMemoryState);
  }

  @NotNull
  public DfaMemoryState getMemoryState() {
    return myBeforeMemoryState;
  }

  public String toString() {
    return getInstruction().getIndex() + " " + getInstruction() + ":   " + getMemoryState();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DfaInstructionState state = (DfaInstructionState)o;
    return Objects.equals(myBeforeMemoryState, state.myBeforeMemoryState) &&
           Objects.equals(myInstruction, state.myInstruction);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myBeforeMemoryState, myInstruction);
  }

  @Override
  public int compareTo(@NotNull DfaInstructionState o) {
    return Integer.compare(myInstruction.getIndex(), o.myInstruction.getIndex());
  }
}