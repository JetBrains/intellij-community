/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 1:11:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public class BinopInstruction extends BranchingInstruction {
  private final String myOperationSign;
  private boolean myIsInstanceofRedundant = true;
  private boolean myIsReachable = false;
  private boolean myCanBeNullInInstanceof = false;

  public BinopInstruction(@NonNls String opSign, PsiElement psiAnchor) {
    if (opSign != null &&
        ("==".equals(opSign) || "!=".equals(opSign) || "instanceof".equals(opSign))) {
      myOperationSign = opSign;
      if (!"instanceof".equals(opSign)) myIsInstanceofRedundant = false;
    }
    else {
      myOperationSign = null;
      myIsInstanceofRedundant = false;
    }

    setPsiAnchor(psiAnchor);
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    myIsReachable = true;
    final Instruction next = runner.getInstruction(getIndex() + 1);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    if (myOperationSign != null) {
      final DfaValueFactory factory = runner.getFactory();
      if (("==".equals(myOperationSign) || "!=".equals(myOperationSign)) &&
          dfaLeft instanceof DfaConstValue && dfaRight instanceof DfaConstValue) {
        boolean negated = "!=".equals(myOperationSign) ^ (memState.canBeNaN(dfaLeft) || memState.canBeNaN(dfaRight));
        if (dfaLeft == dfaRight ^ negated) {
          memState.push(factory.getConstFactory().getTrue());
          setTrueReachable();
        }
        else {
          memState.push(factory.getConstFactory().getFalse());
          setFalseReachable();
        }
        return new DfaInstructionState[]{new DfaInstructionState(next, memState)};
      }

      boolean negated = memState.canBeNaN(dfaLeft) || memState.canBeNaN(dfaRight);
      DfaRelationValue dfaRelation = factory.getRelationFactory().create(dfaLeft, dfaRight, myOperationSign, negated);
      if (dfaRelation != null) {
        myCanBeNullInInstanceof = true;
        ArrayList<DfaInstructionState> states = new ArrayList<DfaInstructionState>();

        final DfaMemoryState trueCopy = memState.createCopy();
        if (trueCopy.applyCondition(dfaRelation)) {
          trueCopy.push(factory.getConstFactory().getTrue());
          setTrueReachable();
          states.add(new DfaInstructionState(next, trueCopy));
        }

        final DfaMemoryState falseCopy = memState;
        if (falseCopy.applyCondition(dfaRelation.createNegated())) {
          falseCopy.push(factory.getConstFactory().getFalse());
          setFalseReachable();
          states.add(new DfaInstructionState(next, falseCopy));
          if (myIsInstanceofRedundant && !falseCopy.isNull(dfaLeft)) {
            myIsInstanceofRedundant = false;
          }
        }

        return states.toArray(new DfaInstructionState[states.size()]);
      }
      else {
        if (PsiKeyword.INSTANCEOF.equals(myOperationSign) &&
            (dfaLeft instanceof DfaTypeValue || dfaLeft instanceof DfaNotNullValue) &&
            dfaRight instanceof DfaTypeValue) {
          final PsiType leftType;
          if (dfaLeft instanceof DfaNotNullValue) {
            leftType = ((DfaNotNullValue)dfaLeft).getType();
          }
          else {
            leftType = ((DfaTypeValue)dfaLeft).getType();
            myCanBeNullInInstanceof = true;
          }

          if (!((DfaTypeValue)dfaRight).getType().isAssignableFrom(leftType)) {
            myIsInstanceofRedundant = false;
          }
        }
        else {
          myIsInstanceofRedundant = false;
        }
        memState.push(DfaUnknownValue.getInstance());
      }
    }
    else {
      memState.push(DfaUnknownValue.getInstance());
    }

    return new DfaInstructionState[]{new DfaInstructionState(next, memState)};
  }

  public boolean isInstanceofRedundant() {
    return myIsInstanceofRedundant && !isConditionConst() && myIsReachable;
  }

  public boolean canBeNull() {
    return myCanBeNullInInstanceof;
  }

  public String toString() {
    return "BINOP " + myOperationSign;
  }
}
