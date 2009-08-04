/*
 * Copyright (c) 2000-2009 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.psi.PsiExpression;

/**
 * @author peter
 */
public abstract class InstructionVisitor {

  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    memState.push(memState.pop());
    return nextInstruction(instruction, runner, memState);
  }

  protected static DfaInstructionState[] nextInstruction(Instruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), memState)};
  }

  public DfaInstructionState[] visitInstanceof(InstanceofInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return visitBinop(instruction, runner, memState);
  }

  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    memState.pop();
    memState.push(DfaUnknownValue.getInstance());
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitConditionalGoto(ConditionalGotoInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitEmptyStack(EmptyStackInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitFlushVariable(FlushVariableInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    //noinspection UnusedDeclaration
    for (PsiExpression arg : instruction.getArgs()) {
      memState.pop();
    }

    memState.pop(); //qualifier
    memState.push(DfaUnknownValue.getInstance());
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitCast(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return visitMethodCall(instruction, runner, memState);
  }

  public DfaInstructionState[] visitNot(NotInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return nextInstruction(instruction, runner, memState);
  }

}
