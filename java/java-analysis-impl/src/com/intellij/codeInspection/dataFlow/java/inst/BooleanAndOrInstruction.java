// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.booleanValue;

/**
 * Non-short-circuit and/or instruction
 */
public class BooleanAndOrInstruction extends ExpressionPushingInstruction {
  private final boolean myOr;

  /**
   * @param or whether it's 'or' (otherwise, it's 'and')
   * @param anchor to bind instruction to
   */
  public BooleanAndOrInstruction(boolean or, @Nullable DfaAnchor anchor) {
    super(anchor);
    myOr = or;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaRight = stateBefore.pop();
    DfaValue dfaLeft = stateBefore.pop();

    List<DfaInstructionState> result = new ArrayList<>(2);
    DfaMemoryState copy = stateBefore.createCopy();
    DfaCondition cond = dfaRight.eq(booleanValue(myOr));
    if (copy.applyCondition(cond)) {
      pushResult(interpreter, copy, booleanValue(myOr));
      result.add(nextState(interpreter, copy));
    }
    if (stateBefore.applyCondition(cond.negate())) {
      pushResult(interpreter, stateBefore, dfaLeft);
      result.add(nextState(interpreter, stateBefore));
    }
    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public String toString() {
    return myOr ? "OR" : "AND";
  }
}
