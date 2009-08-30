package com.intellij.psi.controlFlow;

public class CommentInstruction extends SimpleInstruction {
  private final String myText;

  public CommentInstruction(String text) {
    myText = text;
  }

  public String toString() {
    return ";  " + myText;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitCommentInstruction(this, offset, nextOffset);
  }
}
