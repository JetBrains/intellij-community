// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An instruction to eval unknown value (taking some operands from the stack)
 */
public class EvalUnknownInstruction extends EvalInstruction {
  private final @NotNull DfType myResult;

  /**
   * @param anchor   PsiExpression to anchor to
   * @param operands number of operands
   */
  public EvalUnknownInstruction(@Nullable DfaAnchor anchor, int operands) {
    this(anchor, operands, DfType.TOP);
  }

  /**
   * @param anchor   PsiExpression to anchor to
   * @param operands number of operands
   * @param result result to push
   */
  public EvalUnknownInstruction(@Nullable DfaAnchor anchor, int operands, @NotNull DfType result) {
    super(anchor, operands);
    myResult = result;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    return factory.fromDfType(myResult);
  }

  @Override
  public String toString() {
    return "EVAL_UNKNOWN " + getOperands();
  }
}
