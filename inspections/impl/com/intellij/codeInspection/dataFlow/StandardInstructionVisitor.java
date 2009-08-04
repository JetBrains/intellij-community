/*
 * Copyright (c) 2000-2009 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

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
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue) dfaDest;
      final PsiVariable psiVariable = var.getPsiVariable();
      if (AnnotationUtil.isAnnotated(psiVariable, AnnotationUtil.NOT_NULL, false)) {
        if (!memState.applyNotNull(dfaSource)) {
          onAssigningToNotNullableVariable(instruction, runner);
        }
      }
      memState.setVarValue(var, dfaSource);
    }

    memState.push(dfaDest);

    return nextInstruction(instruction, runner, memState);
  }

  protected void onAssigningToNotNullableVariable(AssignInstruction instruction, DataFlowRunner runner) {}

  @Override
  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState memState) {
    final DfaValue retValue = memState.pop();
    if (!memState.checkNotNullable(retValue)) {
      onNullableReturn(instruction, runner);
    }
    return nextInstruction(instruction, runner, memState);
  }

  protected void onNullableReturn(CheckReturnValueInstruction instruction, DataFlowRunner runner) {}

  @Override
  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValue qualifier = memState.pop();
    if (instruction.getExpression().isPhysical() && !memState.applyNotNull(qualifier)) {
      onInstructionProducesNPE(instruction, runner);
      return DfaInstructionState.EMPTY_ARRAY;
    }

    return nextInstruction(instruction, runner, memState);
  }

  protected void onInstructionProducesNPE(FieldReferenceInstruction instruction, DataFlowRunner runner) {}

  @Override
  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValueFactory factory = runner.getFactory();
    DfaValue dfaExpr = factory.create(instruction.getCasted());
    if (dfaExpr != null) {
      DfaTypeValue dfaType = factory.getTypeFactory().create(instruction.getCastTo());
      DfaRelationValue dfaInstanceof = factory.getRelationFactory().create(dfaExpr, dfaType, "instanceof", false);
      if (dfaInstanceof != null && !memState.applyInstanceofOrNull(dfaInstanceof)) {
        onInstructionProducesCCE(instruction, runner);
      }
    }

    return nextInstruction(instruction, runner, memState);
  }

  protected void onInstructionProducesCCE(TypeCastInstruction instruction, DataFlowRunner runner) {}

  @Override
  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final PsiExpression[] args = instruction.getArgs();
    final boolean[] parametersNotNull = instruction.getParametersNotNull();
    final DfaNotNullValue.Factory factory = runner.getFactory().getNotNullFactory();
    for (int i = 0; i < args.length; i++) {
      final DfaValue arg = memState.pop();
      final int revIdx = args.length - i - 1;
      if (args.length <= parametersNotNull.length && revIdx < parametersNotNull.length && parametersNotNull[revIdx] && !memState.applyNotNull(arg)) {
        onPassingNullParameter(runner, args[revIdx]);
        if (arg instanceof DfaVariableValue) {
          memState.setVarValue((DfaVariableValue)arg, factory.create(((DfaVariableValue)arg).getPsiVariable().getType()));
        }
      }
    }

    @NotNull final DfaValue qualifier = memState.pop();
    try {
      if (!memState.applyNotNull(qualifier)) {
        if (instruction.getMethodType() == MethodCallInstruction.MethodType.UNBOXING) {
          onUnboxingNullable(instruction, runner);
        }
        else {
          onInstructionProducesNPE(instruction, runner);
        }
        if (qualifier instanceof DfaVariableValue) {
          memState.setVarValue((DfaVariableValue)qualifier, factory.create(((DfaVariableValue)qualifier).getPsiVariable().getType()));
        }
      }

      return nextInstruction(instruction, runner, memState);
    }
    finally {
      instruction.pushResult(memState, qualifier);
      if (instruction.shouldFlushFields()) {
        memState.flushFields(runner);
      }
    }
  }

  protected void onInstructionProducesNPE(MethodCallInstruction instruction, DataFlowRunner runner) {}

  protected void onUnboxingNullable(MethodCallInstruction instruction, DataFlowRunner runner) {}

  protected void onPassingNullParameter(DataFlowRunner runner, PsiExpression arg) {}

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
        return nextInstruction(instruction, runner, memState);
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

        //noinspection UnnecessaryLocalVariable
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

    return nextInstruction(instruction, runner, memState);
  }

  public boolean isInstanceofRedundant(InstanceofInstruction instruction) {
    return !myUsefulInstanceofs.contains(instruction) && !instruction.isConditionConst() && myReachable.contains(instruction);
  }

  public boolean canBeNull(BinopInstruction instruction) {
    return myCanBeNullInInstanceof.contains(instruction);
  }
}
