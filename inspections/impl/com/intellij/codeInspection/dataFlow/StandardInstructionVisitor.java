/*
 * Copyright (c) 2000-2009 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;

/**
 * @author peter
 */
public class StandardInstructionVisitor implements InstructionVisitor{
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitInstanceof(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitConditionalGoto(ConditionalGotoInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitEmptyStack(EmptyStackInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitFlushVariable(FlushVariableInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitCast(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitNot(NotInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }

  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return instruction.apply(runner, memState);
  }
}
