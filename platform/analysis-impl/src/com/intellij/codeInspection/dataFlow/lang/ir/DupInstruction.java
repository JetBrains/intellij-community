// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import org.jetbrains.annotations.NotNull;

/**
 * Duplicates top-of-stack value
 */
public class DupInstruction extends Instruction {
  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState memState) {
    memState.push(memState.peek());
    return nextStates(interpreter, memState);
  }

  @Override
  public String toString() {
    return "DUP";
  }
}
