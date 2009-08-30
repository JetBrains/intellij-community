/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 20, 2002
 */
package com.intellij.psi.controlFlow;

public abstract class BranchingInstruction extends InstructionBase {
  public int offset;
  public final Role role;

  public enum Role {
    THEN, ELSE, END
  }

  public BranchingInstruction(int offset, Role role) {
    this.offset = offset;
    this.role = role;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitBranchingInstruction(this, offset, nextOffset);
  }
}
