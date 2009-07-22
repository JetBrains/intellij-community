/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:19 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;


public class GotoInstruction extends Instruction {
  private int myOffset;

  public GotoInstruction(int myOffset) {
    this.myOffset = myOffset;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    Instruction nextInstruction = runner.getInstruction(myOffset);
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return apply(runner, stateBefore);
  }

  public String toString() {
    return "GOTO: " + myOffset;
  }

  public void setOffset(int offset) {
    myOffset = offset;
  }

}
