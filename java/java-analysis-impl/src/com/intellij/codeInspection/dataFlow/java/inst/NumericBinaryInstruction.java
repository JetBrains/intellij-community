// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NumericBinaryInstruction extends EvalInstruction {
  private final @Nullable LongRangeBinOp myBinOp;

  public NumericBinaryInstruction(@Nullable LongRangeBinOp binOp, @Nullable DfaAnchor anchor) {
    super(anchor, 2);
    myBinOp = binOp;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    if (myBinOp == null) return factory.getUnknown();
    DfaValue left = arguments[0];
    DfaValue right = arguments[1];
    DfIntegralType leftType = ObjectUtils.tryCast(state.getDfType(left), DfIntegralType.class);
    DfIntegralType rightType = ObjectUtils.tryCast(state.getDfType(right), DfIntegralType.class);
    if (leftType == null || rightType == null) return factory.getUnknown();
    DfIntegralType resultType = leftType instanceof DfLongType ? DfTypes.LONG : DfTypes.INT;
    return factory.getBinOpFactory().create(left, right, state, resultType, myBinOp);
  }

  public String toString() {
    return myBinOp == null ? "UNKNOWN_NUMERIC_OP" : "NUMERIC_OP " + myBinOp;
  }
}
