package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;

/**
 * @author max
 */
public class GosubInstruction extends Instruction {
  private final int mySubprogramOffset;

  public GosubInstruction(int subprogramOffset) {
    mySubprogramOffset = subprogramOffset;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    final int returnIndex = getIndex() + 1;
    memState.pushOffset(returnIndex);
    Instruction nextInstruction = runner.getInstruction(mySubprogramOffset);
    return new DfaInstructionState[] {new DfaInstructionState(nextInstruction, memState)};
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return apply(runner, stateBefore);
  }

  public String toString() {
    return "GOSUB: " + mySubprogramOffset;
  }
}
