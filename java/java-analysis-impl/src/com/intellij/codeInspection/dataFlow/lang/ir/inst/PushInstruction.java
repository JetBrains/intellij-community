// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * An instruction that pushes given value to the stack
 */
public class PushInstruction extends EvalInstruction {
  private final @NotNull DfaValue myValue;

  public PushInstruction(@NotNull DfaValue value, DfaAnchor place) {
    super(place, 0);
    myValue = value;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    var instruction = new PushInstruction(getValue().bindToFactory(factory), getDfaAnchor());
    instruction.setIndex(getIndex());
    return instruction;
  }

  @NotNull
  public DfaValue getValue() {
    return myValue;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return myValue;
  }

  public String toString() {
    return "PUSH " + myValue;
  }
}
