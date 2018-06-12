// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;

/**
 * Marks end of static or instance initializer
 */
public class EndOfInitializerInstruction extends Instruction {
  private final boolean myStatic;

  public EndOfInitializerInstruction(boolean isStatic) {
    myStatic = isStatic;
  }

  public boolean isStatic() {
    return myStatic;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitEndOfInitializer(this, runner, stateBefore);
  }
}
