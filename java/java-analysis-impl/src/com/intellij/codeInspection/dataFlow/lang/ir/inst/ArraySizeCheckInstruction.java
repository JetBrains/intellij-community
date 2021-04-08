// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArraySizeCheckInstruction extends Instruction {
  private final @NotNull PsiExpression myExpression;
  private final @Nullable DfaControlTransferValue myTransferValue;

  public ArraySizeCheckInstruction(@NotNull PsiExpression expression,
                                   @Nullable DfaControlTransferValue value) {
    myExpression = expression;
    myTransferValue = value;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myTransferValue == null) return this;
    var instruction = new ArraySizeCheckInstruction(myExpression, myTransferValue.bindToFactory(factory));
    instruction.setIndex(getIndex());
    return instruction;
  }

  public @NotNull PsiExpression getExpression() {
    return myExpression;
  }

  @Nullable
  public DfaControlTransferValue getNegativeSizeExceptionTransfer() {
    return myTransferValue;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner,
                                      DfaMemoryState stateBefore,
                                      InstructionVisitor visitor) {
    return visitor.visitArraySizeCheck(this, runner, stateBefore);
  }

  @Override
  public String toString() {
    return "CHECK_ARRAY_SIZE";
  }
}
