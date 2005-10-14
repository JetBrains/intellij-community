/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 2:32:35 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;

public class PopInstruction extends Instruction {
  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState)};
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "POP";
  }
}
