package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiVariable;

public class ReadVariableInstruction extends SimpleInstruction {
  public final PsiVariable variable;

  public ReadVariableInstruction(PsiVariable variable) {
    this.variable = variable;
  }

  public String toString() {
    return "READ " + variable.getName();
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitReadVariableInstruction(this, offset, nextOffset);
  }
}
