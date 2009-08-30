package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;

public class GoToInstruction extends BranchingInstruction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.GoToInstruction");

  public final boolean isReturn; //true if goto has been generated as a result of return statement

  public GoToInstruction(int offset) {
    this(offset, BranchingInstruction.Role.END);
  }
  public GoToInstruction(int offset, Role role) {
    this (offset,role,false);
  }
  public GoToInstruction(int offset, Role role, boolean isReturn) {
    super(offset, role);
    this.isReturn = isReturn;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    final String sRole = "["+role.toString()+"]";
    return "GOTO " + sRole + " " + offset + (isReturn ? " RETURN" : "");
  }

  public int nNext() { return 1; }

  public int getNext(int index, int no) {
    LOG.assertTrue(no == 0);
    return offset;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitGoToInstruction(this, offset, nextOffset);
  }
}
