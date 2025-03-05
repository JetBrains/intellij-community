// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * An instruction that pushes the value of given DfType to the stack
 */
public class PushValueInstruction extends EvalInstruction {
  private final @NotNull DfType myValue;

  public PushValueInstruction(@NotNull DfType value, DfaAnchor place) {
    super(place, 0);
    myValue = value;
  }

  public PushValueInstruction(@NotNull DfType value) {
    this(value, null);
  }

  public @NotNull DfType getValue() {
    return myValue;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return factory.fromDfType(myValue);
  }

  @Override
  public String toString() {
    return "PUSH_VAL " + myValue;
  }
}
