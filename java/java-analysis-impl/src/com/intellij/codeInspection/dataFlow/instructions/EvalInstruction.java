// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * An instruction that takes fixed number of operands from the stack and computes a single result without branching.
 * Assumed to be always successful (without exception branches).
 */
public abstract class EvalInstruction extends ExpressionPushingInstruction<PsiExpression> {
  private final int myOperands;

  /**
   * @param expression PsiExpression to anchor to
   * @param operands number of operands
   */
  protected EvalInstruction(PsiExpression expression, int operands) {
    super(expression);
    myOperands = operands;
  }

  /**
   * @return number of operands
   */
  public final int getOperands() {
    return myOperands;
  }

  @Override
  public final DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitEval(this, runner, stateBefore);
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
