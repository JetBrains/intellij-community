/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import one.util.streamex.IntStreamEx;

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
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    List<DfaValue> removed = IntStreamEx.range(myCount).mapToObj(idx -> stateBefore.pop()).toList();
    IntStreamEx.of(myReplacement).elements(removed).forEach(stateBefore::push);
    Instruction nextInstruction = runner.getInstruction(getIndex() + 1);
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, stateBefore)};
  }

  public String toString() {
    return "SPLICE [" + myCount + "] -> " + Arrays.toString(myReplacement);
  }
}
