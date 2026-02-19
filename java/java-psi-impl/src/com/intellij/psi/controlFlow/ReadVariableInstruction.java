// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

public final class ReadVariableInstruction extends SimpleInstruction {
  public final @NotNull PsiVariable variable;

  ReadVariableInstruction(@NotNull PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public String toString() {
    return "READ " + variable.getName();
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitReadVariableInstruction(this, offset, nextOffset);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return variable.equals(((ReadVariableInstruction)o).variable);
  }

  @Override
  public int hashCode() {
    return 351 + variable.hashCode();
  }
}
