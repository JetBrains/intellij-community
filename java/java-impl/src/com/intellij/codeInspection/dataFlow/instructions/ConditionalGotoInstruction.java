/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:29 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;


import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiElement;

public class ConditionalGotoInstruction extends BranchingInstruction {
  private int myOffset;
  private final boolean myIsNegated;

  public ConditionalGotoInstruction(int myOffset, boolean isNegated, PsiElement psiAnchor) {
    this.myOffset = myOffset;
    myIsNegated = isNegated;
    setPsiAnchor(psiAnchor);
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitConditionalGoto(this, runner, stateBefore);
  }

  public String toString() {
    return "cond_goto " + myOffset;
  }

  public int getOffset() {
    return myOffset;
  }

  public void setOffset(int offset) {
    myOffset = offset;
  }
}
