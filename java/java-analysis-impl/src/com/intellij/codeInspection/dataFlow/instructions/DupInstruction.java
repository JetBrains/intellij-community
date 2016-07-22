/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class DupInstruction extends Instruction {
  private final int myValueCount;
  private final int myDuplicationCount;

  public DupInstruction() {
    this(1, 1);
  }

  public DupInstruction(int valueCount, int duplicationCount) {
    myValueCount = valueCount;
    myDuplicationCount = duplicationCount;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState memState, InstructionVisitor visitor) {
    if (myDuplicationCount == 1 && myValueCount == 1) {
      memState.push(memState.peek());
    } else {
      List<DfaValue> values = new ArrayList<>(myValueCount);
      for (int i = 0; i < myValueCount; i++) {
        values.add(memState.pop());
      }
      for (int j = 0; j < myDuplicationCount + 1; j++) {
        for (int i = values.size() - 1; i >= 0; i--) {
          memState.push(values.get(i));
        }
      }
    }
    Instruction nextInstruction = runner.getInstruction(getIndex() + 1);
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
  }

  public String toString() {
    return "DUP(" + myValueCount + " top stack values, " + myDuplicationCount + " times)";
  }
}
