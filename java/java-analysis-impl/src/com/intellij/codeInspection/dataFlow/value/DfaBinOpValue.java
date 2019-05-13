// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a value like "variable+var/const", "variable-var/const".
 * Stored on the stack only (don't participate in equivalences)
 */
public class DfaBinOpValue extends DfaValue {
  private final @NotNull DfaVariableValue myLeft;
  private final @NotNull DfaValue myRight;
  private final boolean myLong;
  private final BinOp myOp;

  private DfaBinOpValue(@NotNull DfaVariableValue left, @NotNull DfaValue right, boolean isLong, BinOp op) {
    super(left.getFactory());
    assert (right instanceof DfaConstValue && op != BinOp.MINUS) || (right instanceof DfaVariableValue && op != BinOp.REM);
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

  @Override
  public boolean dependsOn(DfaVariableValue other) {
    return myLeft.dependsOn(other) || myRight.dependsOn(other);
  }

  public BinOp getOperation() {
    return myOp;
  }

  @Override
  public String toString() {
    String delimiter;
    if (myOp == BinOp.PLUS && myRight instanceof DfaConstValue &&
        ((DfaConstValue)myRight).getValue() instanceof Long &&
        (Long)((DfaConstValue)myRight).getValue() < 0) {
      delimiter = "";
    } else {
      delimiter = myOp.toString();
    }
    return myLeft + delimiter + myRight;
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
      if (tokenType == null) return DfaUnknownValue.getInstance();
      BinOp op = BinOp.fromTokenType(tokenType);
      if (op != null) {
        DfaValue value = doCreate(left, right, state, isLong, op);
        if (value != null) {
          return value;
        }
      }
      LongRangeSet leftRange = state.getValueFact(left, DfaFactType.RANGE);
      LongRangeSet rightRange = state.getValueFact(right, DfaFactType.RANGE);
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
      if (leftRange != null && rightRange != null) {
        LongRangeSet result = leftRange.binOpFromToken(tokenType, rightRange, isLong);
        return myFactory.getFactValue(DfaFactType.RANGE, result);
      }
      return DfaUnknownValue.getInstance();
    }

    @Nullable
    private DfaValue doCreate(DfaValue left, DfaValue right, DfaMemoryState state, boolean isLong, BinOp op) {
      DfaConstValue leftConst = state.getConstantValue(left);
      if (leftConst != null) {
        left = leftConst;
      }
      DfaConstValue rightConst = state.getConstantValue(right);
      if (rightConst != null) {
        right = rightConst;
      }
      if (op == BinOp.MINUS && state.areEqual(left, right)) {
        return myFactory.getInt(0);
      }
      if (op == BinOp.REM) {
        if (left instanceof DfaVariableValue && right instanceof DfaConstValue) {
          Object value = ((DfaConstValue)right).getValue();
          if (value instanceof Long) {
            long divisor = ((Long)value).longValue();
            if (divisor > 1 && divisor <= Long.SIZE) {
              return doCreate((DfaVariableValue)left, right, isLong, op);
            }
          }
        }
        return null;
      }
      if (left instanceof DfaConstValue && (right instanceof DfaVariableValue || right instanceof DfaBinOpValue) && op == BinOp.PLUS) {
        return doCreate(right, left, state, isLong, op);
      }
      if (left instanceof DfaVariableValue) {
        if (right instanceof DfaVariableValue) {
          if (op == BinOp.PLUS && right.getID() > left.getID()) {
            return doCreate((DfaVariableValue)right, left, isLong, op);
          }
          return doCreate((DfaVariableValue)left, right, isLong, op);
        }
        if (right instanceof DfaConstValue) {
          Long value = ObjectUtils.tryCast(((DfaConstValue)right).getValue(), Long.class);
          if (value != null) {
            if (value == 0) return left;
            if (op == BinOp.MINUS && (isLong || value != Integer.MIN_VALUE)) {
              right = myFactory.getConstFactory().createFromValue(-value, PsiType.LONG);
            }
            return doCreate((DfaVariableValue)left, right, isLong, BinOp.PLUS);
          }
        }
      }
      if (left instanceof DfaBinOpValue) {
        DfaBinOpValue sumValue = (DfaBinOpValue)left;
        if (right instanceof DfaConstValue) {
          if (sumValue.getRight() instanceof DfaConstValue) {
            Long value1 = ObjectUtils.tryCast(((DfaConstValue)sumValue.getRight()).getValue(), Long.class);
            Long value2 = ObjectUtils.tryCast(((DfaConstValue)right).getValue(), Long.class);
            if (value1 != null && value2 != null) {
              if (op == BinOp.MINUS) {
                value2 = -value2;
              }
              long res = value1 + value2;
              right = myFactory.getConstFactory().createFromValue(isLong ? res : (int)res, PsiType.LONG);
              return create(sumValue.getLeft(), right, state, isLong, JavaTokenType.PLUS);
            }
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
