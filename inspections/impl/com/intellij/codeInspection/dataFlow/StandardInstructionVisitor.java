/*
 * Copyright (c) 2000-2009 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.BinopInstruction;
import com.intellij.codeInspection.dataFlow.instructions.InstanceofInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiType;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author peter
 */
public class StandardInstructionVisitor extends InstructionVisitor {
  private final Set<BinopInstruction> myReachable = new THashSet<BinopInstruction>();
  private final Set<BinopInstruction> myCanBeNullInInstanceof = new THashSet<BinopInstruction>();
  private final Set<InstanceofInstruction> myUsefulInstanceofs = new THashSet<InstanceofInstruction>();

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    myReachable.add(instruction);
    final Instruction next = runner.getInstruction(instruction.getIndex() + 1);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    final String opSign = instruction.getOperationSign();
    if (opSign != null) {
      final DfaValueFactory factory = runner.getFactory();
      if (("==".equals(opSign) || "!=".equals(opSign)) &&
          dfaLeft instanceof DfaConstValue && dfaRight instanceof DfaConstValue) {
        boolean negated = "!=".equals(opSign) ^ (memState.canBeNaN(dfaLeft) || memState.canBeNaN(dfaRight));
        if (dfaLeft == dfaRight ^ negated) {
          memState.push(factory.getConstFactory().getTrue());
          instruction.setTrueReachable();
        }
        else {
          memState.push(factory.getConstFactory().getFalse());
          instruction.setFalseReachable();
        }
        return new DfaInstructionState[]{new DfaInstructionState(next, memState)};
      }

      boolean negated = memState.canBeNaN(dfaLeft) || memState.canBeNaN(dfaRight);
      DfaRelationValue dfaRelation = factory.getRelationFactory().create(dfaLeft, dfaRight, opSign, negated);
      if (dfaRelation != null) {
        myCanBeNullInInstanceof.add(instruction);
        ArrayList<DfaInstructionState> states = new ArrayList<DfaInstructionState>();

        final DfaMemoryState trueCopy = memState.createCopy();
        if (trueCopy.applyCondition(dfaRelation)) {
          trueCopy.push(factory.getConstFactory().getTrue());
          instruction.setTrueReachable();
          states.add(new DfaInstructionState(next, trueCopy));
        }

        DfaMemoryState falseCopy = memState;
        if (falseCopy.applyCondition(dfaRelation.createNegated())) {
          falseCopy.push(factory.getConstFactory().getFalse());
          instruction.setFalseReachable();
          states.add(new DfaInstructionState(next, falseCopy));
          if (instruction instanceof InstanceofInstruction && !falseCopy.isNull(dfaLeft)) {
            myUsefulInstanceofs.add((InstanceofInstruction)instruction);
          }
        }

        return states.toArray(new DfaInstructionState[states.size()]);
      }
      else if ("+".equals(opSign)) {
        memState.push(instruction.getNonNullStringValue(factory));
        instruction.setTrueReachable();  // Not a branching instruction actually.
        instruction.setFalseReachable();
      }
      else {
        if (instruction instanceof InstanceofInstruction) {
          if ((dfaLeft instanceof DfaTypeValue || dfaLeft instanceof DfaNotNullValue) && dfaRight instanceof DfaTypeValue) {
            final PsiType leftType;
            if (dfaLeft instanceof DfaNotNullValue) {
              leftType = ((DfaNotNullValue)dfaLeft).getType();
            }
            else {
              leftType = ((DfaTypeValue)dfaLeft).getType();
              myCanBeNullInInstanceof.add(instruction);
            }

            if (!((DfaTypeValue)dfaRight).getType().isAssignableFrom(leftType)) {
              myUsefulInstanceofs.add((InstanceofInstruction)instruction);
            }
          }
          else {
            myUsefulInstanceofs.add((InstanceofInstruction)instruction);
          }
        }
        memState.push(DfaUnknownValue.getInstance());
      }
    }
    else {
      memState.push(DfaUnknownValue.getInstance());
    }

    return new DfaInstructionState[]{new DfaInstructionState(next, memState)};
  }

  public boolean isInstanceofRedundant(InstanceofInstruction instruction) {
    return !myUsefulInstanceofs.contains(instruction) && !instruction.isConditionConst() && myReachable.contains(instruction);
  }

  public boolean canBeNull(BinopInstruction instruction) {
    return myCanBeNullInInstanceof.contains(instruction);
  }
}
