// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a value like "variable+var/const", "variable-var/const".
 * Stored on the stack only (don't participate in equivalences)
 */
public final class DfaBinOpValue extends DfaValue {
  private final @NotNull DfaVariableValue myLeft;
  private final @NotNull DfaValue myRight;
  private final boolean myLong;
  private final BinOp myOp;

  private DfaBinOpValue(@NotNull DfaVariableValue left, @NotNull DfaValue right, boolean isLong, BinOp op) {
    super(left.getFactory());
    assert (right.getDfType() instanceof DfConstantType && op != BinOp.MINUS) ||
           (right instanceof DfaVariableValue && op != BinOp.REM);
    myLeft = left;
    myRight = right;
    myLong = isLong;
    myOp = op;
  }

  @NotNull
  public DfaVariableValue getLeft() {
    return myLeft;
  }

  @NotNull
  public DfaValue getRight() {
    return myRight;
  }

  @Nullable
  @Override
  public PsiType getType() {
    return myLong ? PsiType.LONG : PsiType.INT;
  }

  @NotNull
  @Override
  public DfIntegralType getDfType() {
    return myLong ? DfTypes.LONG : DfTypes.INT;
  }

  @Override
  public boolean dependsOn(DfaVariableValue other) {
    return myLeft.dependsOn(other) || myRight.dependsOn(other);
  }

  public BinOp getOperation() {
    return myOp;
  }

  @Override
  public String toString() {
    String delimiter = myOp.toString();
    if (myOp == BinOp.PLUS && myRight instanceof DfaTypeValue) {
      long value = extractLong((DfaTypeValue)myRight);
      if (value < 0) {
        delimiter = "";
      }
    }
    return myLeft + delimiter + myRight;
  }

  @NotNull
  public DfaValue tryReduceOnCast(DfaMemoryState state, PsiPrimitiveType type) {
    if (!TypeConversionUtil.isIntegralNumberType(type)) return this;
    if ((myOp == BinOp.PLUS || myOp == BinOp.MINUS) &&
        DfLongType.extractRange(state.getDfType(myRight)).castTo(type).equals(LongRangeSet.point(0))) {
      return myLeft;
    }
    if (myOp == BinOp.PLUS &&
        DfLongType.extractRange(state.getDfType(myLeft)).castTo(type).equals(LongRangeSet.point(0))) {
      return myRight;
    }
    return this;
  }

  private static long extractLong(DfaTypeValue right) {
    return ((Number)((DfConstantType<?>)right.getDfType()).getValue()).longValue();
  }

  @NotNull
  public IElementType getTokenType() {
    return myOp.getTokenType();
  }

  public static class Factory {
    private final DfaValueFactory myFactory;
    private final Map<Pair<Long, BinOp>, DfaBinOpValue> myValues = new HashMap<>();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaValue create(DfaValue left, DfaValue right, DfaMemoryState state, boolean isLong, IElementType tokenType) {
      if (tokenType == null) return myFactory.getUnknown();
      BinOp op = BinOp.fromTokenType(tokenType);
      if (op != null) {
        DfaValue value = doCreate(left, right, state, isLong, op);
        if (value != null) {
          return value;
        }
      }
      LongRangeSet leftRange = DfLongType.extractRange(state.getDfType(left));
      LongRangeSet rightRange = DfLongType.extractRange(state.getDfType(right));
      if (tokenType.equals(JavaTokenType.ASTERISK)) {
        if (LongRangeSet.point(1).equals(leftRange)) return right;
        if (LongRangeSet.point(1).equals(rightRange)) return left;
      }
      if (tokenType.equals(JavaTokenType.DIV)) {
        if (LongRangeSet.point(1).equals(rightRange)) return left;
      }
      if (tokenType.equals(JavaTokenType.GTGT) || tokenType.equals(JavaTokenType.LTLT) || tokenType.equals(JavaTokenType.GTGTGT)) {
        if (LongRangeSet.point(0).equals(rightRange)) return left;
      }
      LongRangeSet result = Objects.requireNonNull(leftRange.binOpFromToken(tokenType, rightRange, isLong));
      return myFactory.fromDfType(DfTypes.rangeClamped(result, isLong));
    }

    @Nullable
    private DfaValue doCreate(DfaValue left, DfaValue right, DfaMemoryState state, boolean isLong, BinOp op) {
      DfType leftDfType = state.getDfType(left);
      Number leftConst = DfConstantType.getConstantOfType(leftDfType, Number.class);
      if (leftConst != null) {
        left = left.getFactory().fromDfType(leftDfType);
      }
      DfType rightDfType = state.getDfType(right);
      Number rightConst = DfConstantType.getConstantOfType(rightDfType, Number.class);
      if (rightConst != null) {
        right = right.getFactory().fromDfType(rightDfType);
      }
      if (op == BinOp.MINUS && state.areEqual(left, right)) {
        return myFactory.getInt(0);
      }
      if (op == BinOp.REM) {
        if (left instanceof DfaVariableValue && rightConst != null) {
          long divisor = rightConst.longValue();
          if (divisor > 1 && divisor <= Long.SIZE) {
            return doCreate((DfaVariableValue)left, right, isLong, op);
          }
        }
        return null;
      }
      if (leftConst != null && (right instanceof DfaVariableValue || right instanceof DfaBinOpValue) && op == BinOp.PLUS) {
        return doCreate(right, left, state, isLong, op);
      }
      if (left instanceof DfaVariableValue) {
        if (right instanceof DfaVariableValue) {
          if (op == BinOp.PLUS && right.getID() > left.getID()) {
            return doCreate((DfaVariableValue)right, left, isLong, op);
          }
          return doCreate((DfaVariableValue)left, right, isLong, op);
        }
        if (rightConst != null) {
          long value = rightConst.longValue();
          if (value == 0) return left;
          if (op == BinOp.MINUS) {
            right = myFactory.fromDfType(isLong ? DfTypes.longValue(-value) : DfTypes.intValue(-(int)value));
          }
          return doCreate((DfaVariableValue)left, right, isLong, BinOp.PLUS);
        }
      }
      if (left instanceof DfaBinOpValue) {
        DfaBinOpValue sumValue = (DfaBinOpValue)left;
        if (sumValue.getOperation() != BinOp.PLUS && sumValue.getOperation() != BinOp.MINUS) return null;
        if (rightConst != null) {
          if (sumValue.getRight() instanceof DfaTypeValue) {
            long value1 = extractLong((DfaTypeValue)sumValue.getRight());
            long value2 = rightConst.longValue();
            if (op == BinOp.MINUS) {
              value2 = -value2;
            }
            long res = value1 + value2;
            right = myFactory.fromDfType(isLong ? DfTypes.longValue(res) : DfTypes.intValue((int)res));
            return create(sumValue.getLeft(), right, state, isLong, JavaTokenType.PLUS);
          }
        }
        if (op == BinOp.MINUS && sumValue.getOperation() == BinOp.PLUS) {
          // a+b-a => b; a+b-b => a
          if (state.areEqual(right, sumValue.getLeft())) {
            return sumValue.getRight();
          }
          else if (state.areEqual(right, sumValue.getRight())) {
            return sumValue.getLeft();
          }
        }
      }
      return null;
    }

    @NotNull
    private DfaBinOpValue doCreate(DfaVariableValue left, DfaValue right, boolean isLong, BinOp op) {
      long hash = ((isLong ? 1L : 0L) << 63) | ((long)left.getID() << 32) | right.getID();
      Pair<Long, BinOp> key = Pair.create(hash, op);
      return myValues.computeIfAbsent(key, k -> new DfaBinOpValue(left, right, isLong, op));
    }
  }

  public enum BinOp {
    PLUS("+", JavaTokenType.PLUS), MINUS("-", JavaTokenType.MINUS), REM("%", JavaTokenType.PERC);

    private final String mySign;
    private final IElementType myTokenType;

    BinOp(String sign, IElementType tokenType) {
      mySign = sign;
      myTokenType = tokenType;
    }

    IElementType getTokenType() {
      return myTokenType;
    }

    @Nullable
    public static BinOp fromTokenType(IElementType tokenType) {
      if (PLUS.getTokenType() == tokenType) return PLUS;
      if (MINUS.getTokenType() == tokenType) return MINUS;
      if (REM.getTokenType() == tokenType) return REM;
      return null;
    }

    @Override
    public String toString() {
      return mySign;
    }
  }
}
