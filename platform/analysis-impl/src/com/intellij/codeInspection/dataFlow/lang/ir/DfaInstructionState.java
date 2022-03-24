// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An (instruction, memoryState) pair that represents single possible abstract interpreter state.
 */
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

  /**
   * A helper method to create the resulting states for linear instruction
   * 
   * @param interpreter interpreter that interprets current IR
   * @return an array of single instruction state containing the next instruction. 
   */
  public DfaInstructionState[] nextStates(DataFlowInterpreter interpreter) {
    return myInstruction.nextStates(interpreter, myBeforeMemoryState);
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