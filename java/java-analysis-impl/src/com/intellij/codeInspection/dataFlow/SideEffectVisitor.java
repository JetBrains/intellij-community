// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

/**
 * A visitor which cancels a dataflow once side effect occurs
 * @see DataFlowRunner#cancel()
 */
public class SideEffectVisitor extends StandardInstructionVisitor {
  /**
   * Override this method to allow some variable modifications which do not count as side effects
   *
   * @param variable variable to test
   * @return true if variable modification is not allowed
   */
  protected boolean isModificationAllowed(DfaVariableValue variable) {
    return false;
  }

  @Override
  public DfaInstructionState[] visitFlushFields(FlushFieldsInstruction instruction,
                                                DataFlowRunner runner,
                                                DfaMemoryState memState) {
    runner.cancel();
    return super.visitFlushFields(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitFlushVariable(FlushVariableInstruction instruction,
                                                  DataFlowRunner runner,
                                                  DfaMemoryState memState) {
    if (!isModificationAllowed(instruction.getVariable())) {
      runner.cancel();
    }
    return super.visitFlushVariable(instruction, runner, memState);
  }

  @NotNull
  @Override
  public DfaInstructionState[] visitControlTransfer(@NotNull ControlTransferInstruction instruction,
                                                    @NotNull DataFlowRunner runner, @NotNull DfaMemoryState state) {
    if (instruction instanceof ReturnInstruction && (((ReturnInstruction)instruction).getAnchor() != null ||
                                                     ((ReturnInstruction)instruction).isViaException())) {
      runner.cancel();
    }
    return super.visitControlTransfer(instruction, runner, state);
  }

  @Override
  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction,
                                               DataFlowRunner runner,
                                               DfaMemoryState memState) {
    if (instruction.shouldFlushFields()) {
      runner.cancel();
    }
    return super.visitMethodCall(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dest = memState.pop();
    DfaValue src = memState.peek();
    memState.push(dest);
    if (!(src instanceof DfaVariableValue) || !isModificationAllowed((DfaVariableValue)src)) {
      runner.cancel();
    }
    return super.visitAssign(instruction, runner, memState);
  }
}
