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

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitEmptyStack(this, runner, stateBefore);
  }

  public String toString() {
    return "EMTY_STACK";
  }
}
