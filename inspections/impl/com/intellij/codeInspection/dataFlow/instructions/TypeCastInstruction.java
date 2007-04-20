/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 9, 2002
 * Time: 10:27:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.psi.PsiTypeCastExpression;

public class TypeCastInstruction extends Instruction {
  private final PsiTypeCastExpression myCastExpression;
  private final DfaRelationValue myInstanceofRelation;
  private final boolean myIsSemantical;

  public TypeCastInstruction() {
    myCastExpression = null;
    myInstanceofRelation = null;
    myIsSemantical = true;
  }

  public TypeCastInstruction(PsiTypeCastExpression castExpression, DfaRelationValue instanceofRelation) {
    myIsSemantical = false;
    myCastExpression = castExpression;
    myInstanceofRelation = instanceofRelation;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    if (myIsSemantical) {
      memState.pop();
      memState.push(DfaUnknownValue.getInstance());
    }
    else if (!memState.applyInstanceofOrNull(myInstanceofRelation)) {
      onInstructionProducesCCE(runner);
    }

    return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState)};
  }

  protected void onInstructionProducesCCE(final DataFlowRunner runner) {
  }

  public PsiTypeCastExpression getCastExpression() {
    return myCastExpression;
  }
}
