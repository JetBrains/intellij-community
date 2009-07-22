/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:42 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;


public class EmptyInstruction extends Instruction {
  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    Instruction nextInstruction = runner.getInstruction(getIndex() + 1);
    DfaMemoryState nextState = memState; //.createCopy();
    return new DfaInstructionState[] {new DfaInstructionState(nextInstruction, nextState)};
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return apply(runner, stateBefore);
  }

  public String toString() {
    return "EMPTY";
  }
}
