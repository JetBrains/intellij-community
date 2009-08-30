package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;

/**
 * @author max
 */
public class DupInstruction extends Instruction {

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState memState, InstructionVisitor visitor) {
    final DfaValue a = memState.pop();
    memState.push(a);
    memState.push(a);
    Instruction nextInstruction = runner.getInstruction(getIndex() + 1);
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
  }

  public String toString() {
    return "DUP";
  }
}
