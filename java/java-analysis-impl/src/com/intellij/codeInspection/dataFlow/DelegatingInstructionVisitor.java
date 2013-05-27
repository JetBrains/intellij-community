/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;

/**
 * @author peter
 */
public class DelegatingInstructionVisitor extends InstructionVisitor {
  private final InstructionVisitor myDelegate;

  public DelegatingInstructionVisitor(InstructionVisitor delegate) {
    myDelegate = delegate;
  }

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitAssign(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitInstanceof(InstanceofInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitInstanceof(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitBinop(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState memState) {
    return myDelegate.visitCheckReturnValue(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitConditionalGoto(ConditionalGotoInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitConditionalGoto(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitEmptyStack(EmptyStackInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitEmptyStack(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitFieldReference(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitFlushVariable(FlushVariableInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitFlushVariable(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitMethodCall(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitCast(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitCast(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitNot(NotInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitNot(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitPush(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return myDelegate.visitTypeCast(instruction, runner, memState);
  }
}
