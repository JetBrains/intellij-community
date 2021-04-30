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

package com.intellij.codeInspection.dataFlow.lang.ir.inst;


import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.booleanValue;

public class ConditionalGotoInstruction extends Instruction implements BranchingInstruction {
  private ControlFlow.ControlFlowOffset myOffset;
  private final boolean myIsNegated;
  private final PsiElement myAnchor;

  public ConditionalGotoInstruction(ControlFlow.ControlFlowOffset offset, boolean isNegated, @Nullable PsiElement psiAnchor) {
    myAnchor = psiAnchor;
    myOffset = offset;
    myIsNegated = isNegated;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  @Nullable
  public PsiElement getPsiAnchor() {
    return myAnchor;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner,
                                      @NotNull DfaMemoryState stateBefore) {
    boolean value = !isNegated();
    DfaCondition condTrue = stateBefore.pop().eq(booleanValue(value));
    DfaCondition condFalse = condTrue.negate();

    if (condTrue == DfaCondition.getTrue()) {
      return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(getOffset()), stateBefore)};
    }

    if (condFalse == DfaCondition.getTrue()) {
      return nextStates(runner, stateBefore);
    }

    ArrayList<DfaInstructionState> result = new ArrayList<>(2);

    DfaMemoryState elseState = stateBefore.createCopy();

    if (stateBefore.applyCondition(condTrue)) {
      result.add(new DfaInstructionState(runner.getInstruction(getOffset()), stateBefore));
    }

    if (elseState.applyCondition(condFalse)) {
      result.add(nextState(runner, elseState));
    }

    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  public String toString() {
    return "IF_" + (isNegated() ? "NE" : "EQ") + " " + getOffset();
  }

  public boolean isTarget(boolean whenTrueOnStack, Instruction target) {
    return target.getIndex() == (whenTrueOnStack == myIsNegated ? getIndex() + 1 : getOffset());
  }

  public int getOffset() {
    return myOffset.getInstructionOffset();
  }

  public void setOffset(final int offset) {
    myOffset = new ControlFlow.FixedOffset(offset);
  }
}
