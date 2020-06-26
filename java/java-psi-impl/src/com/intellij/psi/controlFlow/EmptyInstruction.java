// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import org.jetbrains.annotations.NotNull;

public final class EmptyInstruction extends SimpleInstruction {
  public static final EmptyInstruction INSTANCE = new EmptyInstruction();

  private EmptyInstruction() {
  }

  public String toString() {
    return "EMPTY";
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitEmptyInstruction(this, offset, nextOffset);
  }
}
