// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import org.jetbrains.annotations.NotNull;

/**
 * Swaps two top-of-stack values
 */
public class SwapInstruction extends Instruction {

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    final DfaValue a = stateBefore.pop();
    final DfaValue b = stateBefore.pop();
    stateBefore.push(a);
    stateBefore.push(b);
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "SWAP";
  }
}
