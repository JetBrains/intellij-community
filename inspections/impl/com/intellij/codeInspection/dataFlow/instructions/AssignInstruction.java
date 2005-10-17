/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:47:33 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiVariable;

public class AssignInstruction extends Instruction {
  private PsiExpression myRExpression;

  public AssignInstruction(final PsiExpression RExpression) {
    myRExpression = RExpression;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    Instruction nextInstruction = runner.getInstruction(getIndex() + 1);

    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;
      final PsiVariable psiVariable = var.getPsiVariable();
      final PsiModifierList modList = psiVariable.getModifierList();
      if (modList != null && modList.findAnnotation(AnnotationUtil.NOT_NULL) != null) {
        if (!memState.applyNotNull(dfaSource)) {
          runner.onAssigningToNotNullableVariable(myRExpression);
        }
      }
      memState.setVarValue(var, dfaSource);
    }

    memState.push(dfaDest);

    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "ASSIGN";
  }
}
