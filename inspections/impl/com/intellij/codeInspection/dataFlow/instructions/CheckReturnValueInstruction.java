package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiReturnStatement;

/**
 * @author max
 */
public class CheckReturnValueInstruction extends Instruction {
  private final PsiReturnStatement myReturn;

  public CheckReturnValueInstruction(final PsiReturnStatement aReturn) {
    myReturn = aReturn;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    final Instruction nextInstruction = runner.getInstruction(getIndex() + 1);
    final DfaValue retValue = memState.pop();
    if (!memState.checkNotNullable(retValue)) {
      onNullableReturn(runner);
    }
    return new DfaInstructionState[] {new DfaInstructionState(nextInstruction, memState)};
  }

  protected void onNullableReturn(final DataFlowRunner runner) {

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
