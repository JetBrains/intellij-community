package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;

/**
 * @author max
 */
public class SwapInstruction extends Instruction {
  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValue a = memState.pop();
    final DfaValue b = memState.pop();
    memState.push(a);
    memState.push(b);
    Instruction nextInstruction = runner.getInstruction(getIndex() + 1);
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
  }

  public String toString() {
    return "SWAP";
  }
}
