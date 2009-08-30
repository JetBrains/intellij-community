/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:06 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;

public class FlushVariableInstruction extends Instruction {
  private final DfaVariableValue myVariable;

  public FlushVariableInstruction(DfaVariableValue expr) {
    myVariable = expr;
  }

  public DfaVariableValue getVariable() {
    return myVariable;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitFlushVariable(this, runner, stateBefore);
  }

  public String toString() {
    return "FLUSH " + (myVariable != null ? myVariable.toString() : " all fields");
  }
}
