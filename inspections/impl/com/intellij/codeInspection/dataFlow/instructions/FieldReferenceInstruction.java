package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class FieldReferenceInstruction extends Instruction {
  private PsiExpression myExpression;
  private boolean myIsPhysical;
  @Nullable private final String mySyntheticFieldName;

  public FieldReferenceInstruction(PsiExpression expression, @Nullable @NonNls String syntheticFieldName) {
    myExpression = expression;
    myIsPhysical = expression.isPhysical();
    mySyntheticFieldName = syntheticFieldName;
  }
  public FieldReferenceInstruction(PsiReferenceExpression expression) {
    this(expression, null);
  }

  public FieldReferenceInstruction(PsiArrayAccessExpression expression) {
    this(expression, null);
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValue qualifier = memState.pop();
    if (myIsPhysical && !memState.applyNotNull(qualifier)) {
      runner.onInstructionProducesNPE(this);
      return new DfaInstructionState[0];
    }

    return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState)};
  }

  public String toString() {
    return "FIELD_REFERENCE: " + myExpression.getText();
  }

  public PsiExpression getExpression() { return myExpression; }

  public PsiExpression getElementToAssert() {
    if (mySyntheticFieldName != null) return myExpression;
    return myExpression instanceof PsiArrayAccessExpression
           ? ((PsiArrayAccessExpression)myExpression).getArrayExpression()
           : ((PsiReferenceExpression)myExpression).getQualifierExpression();
  }
}
