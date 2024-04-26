// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.controlFlow;

import org.jetbrains.annotations.NotNull;

public abstract class BranchingInstruction extends InstructionBase {
  public int offset;
  public final @NotNull Role role;

  public enum Role {
    THEN, ELSE, END
  }

  public BranchingInstruction(int offset, @NotNull Role role) {
    this.offset = offset;
    this.role = role;
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitBranchingInstruction(this, offset, nextOffset);
  }
}
