/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:47:33 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiExpression;

public class AssignInstruction extends Instruction {
  private final PsiExpression myRExpression;

  public AssignInstruction(PsiExpression RExpression) {
    myRExpression = RExpression;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitAssign(this, runner, stateBefore);
  }

  public PsiExpression getRExpression() {
    return myRExpression;
  }

  public String toString() {
    return "ASSIGN";
  }
}
