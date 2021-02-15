// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.types.DfType;
import org.jetbrains.annotations.NotNull;

public class BoxingInstruction extends Instruction {
  @NotNull private final DfType myTargetType;
  @NotNull private final SpecialField mySpecialField;

  public BoxingInstruction(@NotNull DfType targetType, @NotNull SpecialField field) {
    myTargetType = targetType;
    mySpecialField = field;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitBox(this, runner, stateBefore);
  }

  public @NotNull SpecialField getSpecialField() {
    return mySpecialField;
  }

  public @NotNull DfType getTargetType() {
    return myTargetType;
  }

  @Override
  public String toString() {
    return "BOX";
  }
}
