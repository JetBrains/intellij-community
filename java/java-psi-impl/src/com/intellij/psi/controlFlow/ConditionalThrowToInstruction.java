// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

public class ConditionalThrowToInstruction extends ConditionalBranchingInstruction {

  public ConditionalThrowToInstruction(final int offset) {
    super(offset, null, Role.END);
  }

  public String toString() {
    return "COND_THROW_TO " + offset;
  }

  @Override
  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalThrowToInstruction(this, offset, nextOffset);
  }
}
