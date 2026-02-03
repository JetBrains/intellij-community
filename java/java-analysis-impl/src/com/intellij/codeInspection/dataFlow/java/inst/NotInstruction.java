// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.FALSE;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.TRUE;

public class NotInstruction extends ExpressionPushingInstruction {
  public NotInstruction(@Nullable DfaAnchor anchor) {
    super(anchor);
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaValue = stateBefore.pop();

    DfaMemoryState falseState = stateBefore.createCopy();
    List<DfaInstructionState> result = new ArrayList<>(2);
    if (stateBefore.applyCondition(dfaValue.eq(FALSE))) {
      pushResult(interpreter, stateBefore, TRUE);
      result.add(nextState(interpreter, stateBefore));
    }
    if (falseState.applyCondition(dfaValue.eq(TRUE))) {
      pushResult(interpreter, falseState, FALSE);
      result.add(nextState(interpreter, falseState));
    }

    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public String toString() {
    return "NOT";
  }
}
