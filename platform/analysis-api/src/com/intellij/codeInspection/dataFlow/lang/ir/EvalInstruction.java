// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An instruction that takes fixed number of operands from the stack and computes a single result without branching.
 * Assumed to be always successful (without exception branches).
 */
public abstract class EvalInstruction extends ExpressionPushingInstruction {
  private final int myOperands;

  /**
   * @param anchor PsiExpression to anchor to
   * @param operands number of operands
   */
  protected EvalInstruction(@Nullable DfaAnchor anchor, int operands) {
    super(anchor);
    myOperands = operands;
  }

  /**
   * @return number of operands
   */
  public final int getOperands() {
    return myOperands;
  }

  @Override
  public final DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    int operands = getOperands();
    DfaValue[] args = new DfaValue[operands];
    for (int i = operands - 1; i >= 0; i--) {
      args[i] = stateBefore.pop();
    }
    DfaValue value = eval(interpreter.getFactory(), stateBefore, args);
    pushResult(interpreter, stateBefore, value, args);
    return nextStates(interpreter, stateBefore);
  }

  /**
   * @param factory value factory to use to produce new values
   * @param state state. Must not be modified, could be used to query information only
   * @param arguments operation arguments (length must be the same as {@link #getOperands()} result)
   * @return the evaluated value
   */
  public abstract @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                         @NotNull DfaMemoryState state,
                                         @NotNull DfaValue @NotNull ... arguments);
}
