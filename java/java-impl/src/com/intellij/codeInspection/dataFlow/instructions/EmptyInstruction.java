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

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    Instruction nextInstruction = runner.getInstruction(getIndex() + 1);
    return new DfaInstructionState[] {new DfaInstructionState(nextInstruction, stateBefore)};
  }

  public String toString() {
    return "EMPTY";
  }
}
