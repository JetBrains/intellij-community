package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class FieldReferenceInstruction extends Instruction {
  private final PsiExpression myExpression;
  @Nullable private final String mySyntheticFieldName;

  public FieldReferenceInstruction(@NotNull PsiExpression expression, @Nullable @NonNls String syntheticFieldName) {
    myExpression = expression;
    mySyntheticFieldName = syntheticFieldName;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitFieldReference(this, runner, stateBefore);
  }

  public String toString() {
    return "FIELD_REFERENCE: " + myExpression.getText();
  }

  @NotNull
  public PsiExpression getExpression() {
    return myExpression;
  }

  @Nullable 
  public PsiExpression getElementToAssert() {
    if (mySyntheticFieldName != null) return myExpression;
    return myExpression instanceof PsiArrayAccessExpression
           ? ((PsiArrayAccessExpression)myExpression).getArrayExpression()
           : ((PsiReferenceExpression)myExpression).getQualifierExpression();
  }
}
