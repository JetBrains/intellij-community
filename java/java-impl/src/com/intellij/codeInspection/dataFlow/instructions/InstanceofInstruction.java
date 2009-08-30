package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class InstanceofInstruction extends BinopInstruction {
  @NotNull private PsiExpression myLeft;
  @NotNull private PsiType myCastType;

  public InstanceofInstruction(PsiElement psiAnchor, @NotNull Project project, PsiExpression left, PsiType castType) {
    super(PsiKeyword.INSTANCEOF, psiAnchor, project);
    myLeft = left;
    myCastType = castType;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitInstanceof(this, runner, stateBefore);
  }

  @NotNull
  public PsiExpression getLeft() {
    return myLeft;
  }

  @NotNull
  public PsiType getCastType() {
    return myCastType;
  }
}
