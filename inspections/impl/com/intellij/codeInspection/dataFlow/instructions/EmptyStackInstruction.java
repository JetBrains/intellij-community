/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Mar 15, 2002
 * Time: 5:04:29 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;

public class EmptyStackInstruction extends Instruction {
  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState dfaMemoryState) {
    dfaMemoryState.emptyStack();
    Instruction nextInstruction = runner.getInstruction(getIndex() + 1);
    return new DfaInstructionState[] {new DfaInstructionState(nextInstruction, dfaMemoryState)};
  }

  public String toString() {
    return "EMTY_STACK";
  }
}
