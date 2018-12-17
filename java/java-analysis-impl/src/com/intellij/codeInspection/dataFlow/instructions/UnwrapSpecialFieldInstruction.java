// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Instruction to push a field qualified by the value on the stack
 */
public class UnwrapSpecialFieldInstruction extends Instruction {
  @Nullable private final PsiType myTargetType;
  @NotNull private final SpecialField mySpecialField;

  public UnwrapSpecialFieldInstruction(@NotNull SpecialField specialField, @Nullable PsiType targetType) {
    myTargetType = targetType;
    mySpecialField = specialField;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitUnwrapField(this, runner, stateBefore);
  }

  @NotNull
  public SpecialField getSpecialField() {
    return mySpecialField;
  }

  @Nullable
  public PsiType getTargetType() {
    return myTargetType;
  }

  @Override
  public String toString() {
    return "GET_FIELD " + mySpecialField;
  }
}
