package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiVariable;

public class WriteVariableInstruction extends SimpleInstruction {
  public final PsiVariable variable;

  public WriteVariableInstruction(PsiVariable variable) {
    this.variable = variable;
  }

  public String toString() {
    return "WRITE " + variable.getName();
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitWriteVariableInstruction(this, offset, nextOffset);
  }
}
