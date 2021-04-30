// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import org.jetbrains.annotations.NotNull;

public class SwapInstruction extends Instruction {

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    final DfaValue a = stateBefore.pop();
    final DfaValue b = stateBefore.pop();
    stateBefore.push(a);
    stateBefore.push(b);
    return nextStates(runner, stateBefore);
  }

  public String toString() {
    return "SWAP";
  }
}
