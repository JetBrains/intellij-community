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

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.FlushVariableInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import gnu.trove.TObjectIntHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ControlFlow {
  private final List<Instruction> myInstructions = new ArrayList<>();
  private final TObjectIntHashMap<PsiElement> myElementToStartOffsetMap = new TObjectIntHashMap<>();
  private final TObjectIntHashMap<PsiElement> myElementToEndOffsetMap = new TObjectIntHashMap<>();
  private final DfaValueFactory myFactory;

  public ControlFlow(final DfaValueFactory factory) {
    myFactory = factory;
  }

  public Instruction[] getInstructions(){
    return myInstructions.toArray(new Instruction[myInstructions.size()]);
  }

  public int getInstructionCount() {
    return myInstructions.size();
  }

  public ControlFlowOffset getNextOffset() {
    final int currentSize = myInstructions.size();
    return new ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return currentSize;
      }
    };
  }

  public void startElement(PsiElement psiElement) {
    myElementToStartOffsetMap.put(psiElement, myInstructions.size());
  }

  public void finishElement(PsiElement psiElement) {
    myElementToEndOffsetMap.put(psiElement, myInstructions.size());
  }

  public void addInstruction(Instruction instruction) {
    instruction.setIndex(myInstructions.size());
    myInstructions.add(instruction);
  }

  public void removeVariable(@Nullable PsiVariable variable) {
    if (variable == null) return;
    addInstruction(new FlushVariableInstruction(myFactory.getVarFactory().createVariableValue(variable, false)));
  }

  /**
   * @return stream of all accessed variables within this flow
   */
  public Stream<DfaVariableValue> accessedVariables() {
    return StreamEx.of(myInstructions).select(PushInstruction.class)
      .remove(PushInstruction::isReferenceWrite)
      .map(PushInstruction::getValue)
      .select(DfaVariableValue.class).distinct();
  }

  public ControlFlowOffset getStartOffset(final PsiElement element) {
    return new ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return myElementToStartOffsetMap.get(element);
      }
    };
  }

  public ControlFlowOffset getEndOffset(final PsiElement element) {
    return new ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return myElementToEndOffsetMap.get(element);
      }
    };
  }

  public String toString() {
    StringBuilder result = new StringBuilder();
    final List<Instruction> instructions = myInstructions;

    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      result.append(Integer.toString(i)).append(": ").append(instruction.toString());
      result.append("\n");
    }
    return result.toString();
  }

  public abstract static class ControlFlowOffset {
    public abstract int getInstructionOffset();

    @Override
    public String toString() {
      return String.valueOf(getInstructionOffset());
    }
  }

  static ControlFlowOffset deltaOffset(final ControlFlowOffset delegate, final int delta) {
    return new ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return delegate.getInstructionOffset() + delta;
      }
    };
  }

}