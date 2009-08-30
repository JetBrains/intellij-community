package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiReturnStatement;

/**
 * @author max
 */
public class CheckReturnValueInstruction extends Instruction {
  private final PsiReturnStatement myReturn;

  public CheckReturnValueInstruction(final PsiReturnStatement aReturn) {
    myReturn = aReturn;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitCheckReturnValue(this, runner, stateBefore);
  }

  public PsiReturnStatement getReturn() {
    return myReturn;
  }

  public String toString() {
    return "CheckReturnValue";
  }
}
