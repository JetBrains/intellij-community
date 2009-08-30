package com.intellij.psi.controlFlow;

import org.jetbrains.annotations.NotNull;


public class CallInstruction extends GoToInstruction {
  public final ControlFlowStack stack;
  public int procBegin;
  public int procEnd;

  public CallInstruction(int procBegin, int procEnd, @NotNull ControlFlowStack stack) {
    super(procBegin);
    this.stack = stack;
    this.procBegin = procBegin;
    this.procEnd = procEnd;
  }

  public String toString() {
    return "CALL " + offset ;
  }

  public void execute(int returnOffset) {
    synchronized (stack) {
      stack.push(returnOffset, this);
    }
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitCallInstruction(this, offset, nextOffset);
  }
}
