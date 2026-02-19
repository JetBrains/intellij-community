// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Pop several elements from the stack and replace them with some of them (possibly duplicating, swapping, removing some, etc.)
 */
public class SpliceInstruction extends Instruction {
  private final int myCount;
  private final int[] myReplacement;

  public SpliceInstruction(int count, int... replacement) {
    myCount = count;
    myReplacement = replacement;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    List<DfaValue> removed = IntStreamEx.range(myCount).mapToObj(idx -> stateBefore.pop()).toList();
    IntStreamEx.of(myReplacement).elements(removed).forEach(stateBefore::push);
    Instruction nextInstruction = interpreter.getInstruction(getIndex() + 1);
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, stateBefore)};
  }

  @Override
  public String toString() {
    return "SPLICE [" + myCount + "] -> " + Arrays.toString(myReplacement);
  }
}
