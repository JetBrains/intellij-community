// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;


import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Conditionally jump if the value on stack top is equal to the specified value (top-of-stack value is being popped)
 */
public class ConditionalGotoInstruction extends Instruction {
  private ControlFlow.ControlFlowOffset myOffset;
  private final @NotNull DfType myCompareTo;
  private final PsiElement myAnchor;

  /**
   * @param offset target offset to jump to
   * @param compareTo value to compare to
   */
  public ConditionalGotoInstruction(ControlFlow.ControlFlowOffset offset, @NotNull DfType compareTo) {
    this(offset, compareTo, null);
  }
  
  public ConditionalGotoInstruction(ControlFlow.ControlFlowOffset offset, @NotNull DfType compareTo, @Nullable PsiElement psiAnchor) {
    myAnchor = psiAnchor;
    myOffset = offset;
    myCompareTo = compareTo;
  }

  /**
   * @return PSI element associated with this instruction
   * @deprecated used in "Find the cause" feature only. Will be removed
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public PsiElement getPsiAnchor() {
    return myAnchor;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaCondition condTrue = stateBefore.pop().eq(myCompareTo);
    DfaCondition condFalse = condTrue.negate();

    if (condTrue == DfaCondition.getTrue()) {
      return new DfaInstructionState[] {new DfaInstructionState(interpreter.getInstruction(getOffset()), stateBefore)};
    }

    if (condFalse == DfaCondition.getTrue()) {
      return nextStates(interpreter, stateBefore);
    }

    ArrayList<DfaInstructionState> result = new ArrayList<>(2);

    DfaMemoryState elseState = stateBefore.createCopy();

    if (stateBefore.applyCondition(condTrue)) {
      result.add(new DfaInstructionState(interpreter.getInstruction(getOffset()), stateBefore));
    }

    if (elseState.applyCondition(condFalse)) {
      result.add(nextState(interpreter, elseState));
    }

    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public int @NotNull [] getSuccessorIndexes() {
    int offset = getOffset();
    return offset == getIndex() + 1 ? new int[] {offset} : new int[] {getIndex() + 1, offset};
  }

  public String toString() {
    return "IF_EQ " + myCompareTo + " " + getOffset();
  }

  public boolean isTarget(@NotNull DfType valueOnStack, @NotNull Instruction target) {
    return target.getIndex() == (valueOnStack.equals(myCompareTo) ? getOffset() : getIndex() + 1);
  }

  public int getOffset() {
    return myOffset.getInstructionOffset();
  }

  public void setOffset(final int offset) {
    myOffset = new ControlFlow.FixedOffset(offset);
  }
}
