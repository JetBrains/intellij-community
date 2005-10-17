/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 1:25:41 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;

public class PushInstruction extends Instruction {
  private final DfaValue myValue;

  public PushInstruction(DfaValue value) {
    this.myValue = value != null ? value : DfaUnknownValue.getInstance();
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    memState.push(myValue);
    return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(getIndex() + 1),memState)};
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "PUSH " + myValue;
  }
}
