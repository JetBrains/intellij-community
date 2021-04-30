// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * A base class for all instructions of DFA IR that could be interpreted.
 * There are language agnostic instructions as well as language specific ones.
 * Every instruction knows how to interpret itself.
 */
public abstract class Instruction {
  private int myIndex = -1;

  /**
   * Interpret the instruction and return the resulting instruction states
   *
   * @param runner runner that interprets current IR
   * @param stateBefore input memory state. Could be modified during the interpretation
   * @return an array of output memory states. Non-branching instructions return an array of one element.
   * Returning an empty array means that the interpretation stops here 
   * (e.g. 'return' instruction or throwing an exception).
   */
  public abstract DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore);

  /**
   * Set the current instruction index within the IR it belongs to. Can be done only once.
   * @param index instruction index to set.
   */
  public void setIndex(int index) {
    if (myIndex != -1) {
      throw new IllegalStateException("Index is already set");
    }
    myIndex = index;
  }

  /**
   * @return the instruction index within the IR it belongs to.
   */
  public int getIndex() {
    return myIndex;
  }

  /**
   * Copy instruction to another factory. May return itself if the instruction does not depend on the factory.
   *
   * @param factory factory to place the instruction
   * @return copy that bound to the supplied factory, or itself if the instruction does not depend on the factory.
   */
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return this;
  }

  /**
   * A helper method to create the resulting states for linear instruction 
   * @param runner runner that interprets current IR
   * @param memState resulting memory state
   * @return an array of a single instruction state that refers to the next instruction and the supplied memory state
   */
  protected final @NotNull DfaInstructionState @NotNull [] nextStates(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState memState) {
    return new DfaInstructionState[]{nextState(runner, memState)};
  }

  /**
   * A helper method to create the resulting state pointing to the next instruction 
   * @param runner runner that interprets current IR
   * @param memState resulting memory state
   * @return an instruction state that refers to the next instruction and the supplied memory state
   */
  protected final @NotNull DfaInstructionState nextState(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState memState) {
    return new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState);
  }
}
