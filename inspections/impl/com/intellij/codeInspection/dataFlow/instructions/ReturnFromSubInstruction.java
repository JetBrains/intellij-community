package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;

/**
 * @author max
 */
public class ReturnFromSubInstruction extends Instruction{
  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    int offset = memState.popOffset();
    return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(offset), memState)};
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "RETURN_FROM_SUB";
  }
}
