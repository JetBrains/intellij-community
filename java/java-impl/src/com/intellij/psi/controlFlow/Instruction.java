package com.intellij.psi.controlFlow;

public interface Instruction extends Cloneable {
  Instruction clone();
  int   nNext ();
  int getNext (int index, int no);

  void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset);
}
