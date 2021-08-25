// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * An instruction to eval unknown value (taking some operands from the stack)
 */
public class EvalUnknownInstruction extends EvalInstruction {
  /**
   * @param anchor   PsiExpression to anchor to
   * @param operands number of operands
   */
  public EvalUnknownInstruction(DfaAnchor anchor, int operands) {
    super(anchor, operands);
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    return factory.getUnknown();
  }

  @Override
  public String toString() {
    return "EVAL_UNKNOWN " + getOperands();
  }
}
