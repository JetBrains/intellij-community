// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
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
    DfType leftType = state.getDfType(left);
    DfType rightType = state.getDfType(right);
    if (leftType instanceof DfIntegralType && rightType instanceof DfIntegralType) {
      DfIntegralType resultType = leftType instanceof DfLongType ? DfTypes.LONG : DfTypes.INT;
      return factory.getBinOpFactory().create(left, right, state, resultType, myBinOp);
    }
    if (leftType instanceof DfDoubleConstantType d1 && rightType instanceof DfDoubleConstantType d2) {
      return factory.fromDfType(eval(myBinOp, d1.getValue(), d2.getValue()));
    }
    if (leftType instanceof DfFloatConstantType f1 && rightType instanceof DfFloatConstantType f2) {
      return factory.fromDfType(eval(myBinOp, f1.getValue(), f2.getValue()));
    }
    return factory.getUnknown();
  }

  private static DfType eval(LongRangeBinOp op, float f1, float f2) {
    return switch (op) {
      case PLUS -> DfTypes.floatValue(f1 + f2).makeWide();
      case MINUS -> DfTypes.floatValue(f1 - f2).makeWide();
      case MUL -> DfTypes.floatValue(f1 * f2).makeWide();
      case DIV -> DfTypes.floatValue(f1 / f2).makeWide();
      case MOD -> DfTypes.floatValue(f1 % f2).makeWide();
      default -> DfType.TOP;
    };
  }

  private static DfType eval(LongRangeBinOp op, double d1, double d2) {
    return switch (op) {
      case PLUS -> DfTypes.doubleValue(d1 + d2).makeWide();
      case MINUS -> DfTypes.doubleValue(d1 - d2).makeWide();
      case MUL -> DfTypes.doubleValue(d1 * d2).makeWide();
      case DIV -> DfTypes.doubleValue(d1 / d2).makeWide();
      case MOD -> DfTypes.doubleValue(d1 % d2).makeWide();
      default -> DfType.TOP;
    };
  }

  @Override
  public String toString() {
    return myBinOp == null ? "UNKNOWN_NUMERIC_OP" : "NUMERIC_OP " + myBinOp;
  }
}
