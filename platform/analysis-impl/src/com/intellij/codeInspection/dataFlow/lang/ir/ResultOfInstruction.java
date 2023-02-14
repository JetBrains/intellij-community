// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Instruction that does nothing but allows to attach an anchor to the top-of-stack
 */
public class ResultOfInstruction extends EvalInstruction {
  public ResultOfInstruction(@NotNull DfaAnchor anchor) {
    super(anchor, 1);
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return arguments[0];
  }

  public String toString() {
    return "RESULT_OF " + getDfaAnchor();
  }

  @NotNull
  @Override
  public DfaAnchor getDfaAnchor() {
    return Objects.requireNonNull(super.getDfaAnchor());
  }
}
