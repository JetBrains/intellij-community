// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a value like "variable+var/const", "variable-var/const", or "variable % const".
 * Stored on the stack only (don't participate in equivalences)
 */
public final class DfaBinOpValue extends DfaValue {
  private final @NotNull DfaVariableValue myLeft;
  private final @NotNull DfaValue myRight;
  private final @NotNull DfIntegralType myType;
  private final @NotNull LongRangeBinOp myOp;

  private DfaBinOpValue(@NotNull DfaVariableValue left, @NotNull DfaValue right, @NotNull DfIntegralType type, @NotNull LongRangeBinOp op) {
    super(left.getFactory());
    switch (op) {
      case PLUS -> {
        if (!(right.getDfType() instanceof DfConstantType) && !(right instanceof DfaVariableValue)) {
          throw new IllegalArgumentException("RHO must be constant or variable for plus");
        }
      }
      case MINUS -> {
        if (!(right instanceof DfaVariableValue)) {
          throw new IllegalArgumentException("RHO must be variable for minus");
        }
      }
      case MOD -> {
        if (!(right.getDfType() instanceof DfConstantType)) {
          throw new IllegalArgumentException("RHO must be constant for mod");
        }
      }
      default -> throw new IllegalArgumentException("Unsupported op: " + op);
    }
    myLeft = left;
    myRight = right;
    myType = type;
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

  @Override
  public DfaValue bindToFactory(@NotNull DfaValueFactory factory) {
    return factory.getBinOpFactory().doCreate(myLeft.bindToFactory(factory), myRight.bindToFactory(factory), myType, myOp);
  }

  @NotNull
  @Override
  public DfIntegralType getDfType() {
    return myType;
  }

  @Override
  public boolean dependsOn(DfaVariableValue other) {
    return myLeft.dependsOn(other) || myRight.dependsOn(other);
  }

  public @NotNull LongRangeBinOp getOperation() {
    return myOp;
  }

  @Override
  public String toString() {
    String delimiter = myOp.toString();
    if (myOp == LongRangeBinOp.PLUS && myRight instanceof DfaTypeValue) {
      long value = extractLong((DfaTypeValue)myRight);
      if (value < 0) {
        delimiter = "";
      }
    }
    return myLeft + delimiter + myRight;
  }

  private static long extractLong(DfaTypeValue right) {
    return Objects.requireNonNull(right.getDfType().getConstantOfType(Number.class)).longValue();
  }

  public static class Factory {
    private final DfaValueFactory myFactory;
    private record Key(long hash, LongRangeBinOp op, DfType type) {}
    private final Map<Key, DfaBinOpValue> myValues = new HashMap<>();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    /**
     * Create a new value as a result of applying binary operation op to left and right values
     * @param left left value
     * @param right right value
     * @param state memory state
     * @param resultType declared result type
     * @param op operation to apply
     * @return a value that represents a computation result. Could be DfaBinOpValue, DfaTypeValue, or DfaVariableValue
     * depending on how result state is represented better.
     */
    public DfaValue create(DfaValue left, DfaValue right, DfaMemoryState state, DfIntegralType resultType, LongRangeBinOp op) {
      if (op == null) return myFactory.getUnknown();
      DfaValue value = doCreate(left, right, state, resultType, op);
      if (value != null) {
        return value;
      }
      DfIntegralType leftType = ObjectUtils.tryCast(state.getDfType(left), DfIntegralType.class);
      DfIntegralType rightType = ObjectUtils.tryCast(state.getDfType(right), DfIntegralType.class);
      if (leftType == null || rightType == null) {
        return myFactory.fromDfType(resultType);
      }
      if (op == LongRangeBinOp.MUL) {
        if (LongRangeSet.point(1).equals(leftType.getRange())) return right;
        if (LongRangeSet.point(1).equals(rightType.getRange())) return left;
      }
      if (op == LongRangeBinOp.DIV) {
        if (LongRangeSet.point(1).equals(rightType.getRange())) return left;
      }
      if (op == LongRangeBinOp.SHL || op == LongRangeBinOp.SHR || op == LongRangeBinOp.USHR) {
        if (LongRangeSet.point(0).equals(rightType.getRange())) return left;
      }
      DfType resType = leftType.eval(rightType, op);
      return myFactory.fromDfType(resType);
    }

    @Nullable
    private DfaValue doCreate(DfaValue left, DfaValue right, DfaMemoryState state, DfIntegralType resultType, LongRangeBinOp op) {
      if (op != LongRangeBinOp.PLUS && op != LongRangeBinOp.MINUS && op != LongRangeBinOp.MOD) return null;
      DfType leftDfType = state.getDfType(left);
      Number leftConst = leftDfType.getConstantOfType(Number.class);
      if (leftConst != null) {
        left = left.getFactory().fromDfType(leftDfType);
      }
      DfType rightDfType = state.getDfType(right);
      Number rightConst = rightDfType.getConstantOfType(Number.class);
      if (rightConst != null) {
        right = right.getFactory().fromDfType(rightDfType);
      }
      if (op == LongRangeBinOp.MINUS && state.areEqual(left, right)) {
        return myFactory.fromDfType(resultType.meetRange(LongRangeSet.point(0)));
      }
      if (op == LongRangeBinOp.MOD) {
        if (leftDfType instanceof DfIntegralType && rightDfType instanceof DfIntegralType) {
          if (withinDivisorRange(state, left, right, ((DfIntegralType)leftDfType).getRange(), ((DfIntegralType)rightDfType).getRange())) {
            return left;
          }
        }
        if (left instanceof DfaVariableValue && rightConst != null) {
          long divisor = rightConst.longValue();
          if (divisor > 1 && divisor <= Long.SIZE) {
            return doCreate((DfaVariableValue)left, right, resultType, op);
          }
        }
        return null;
      }
      if (leftConst != null && (right instanceof DfaVariableValue || right instanceof DfaBinOpValue) && op == LongRangeBinOp.PLUS) {
        return doCreate(right, left, state, resultType, op);
      }
      if (left instanceof DfaVariableValue) {
        if (right instanceof DfaVariableValue) {
          if (op == LongRangeBinOp.PLUS && right.getID() > left.getID()) {
            return doCreate((DfaVariableValue)right, left, resultType, op);
          }
          return doCreate((DfaVariableValue)left, right, resultType, op);
        }
        if (rightConst != null) {
          long value = rightConst.longValue();
          if (value == 0) return left;
          if (op == LongRangeBinOp.MINUS) {
            right = myFactory.fromDfType(resultType.meetRange(LongRangeSet.point(value).negate(resultType.getLongRangeType())));
          }
          return doCreate((DfaVariableValue)left, right, resultType, LongRangeBinOp.PLUS);
        }
      }
      if (left instanceof DfaBinOpValue) {
        DfaBinOpValue sumValue = (DfaBinOpValue)left;
        if (sumValue.getOperation() != LongRangeBinOp.PLUS && sumValue.getOperation() != LongRangeBinOp.MINUS) return null;
        if (rightConst != null) {
          if (sumValue.getRight() instanceof DfaTypeValue) {
            DfType rightType = sumValue.getRight().getDfType();
            LongRangeSet value1 = ((DfIntegralType)rightType).getRange();
            LongRangeSet value2 = LongRangeSet.point(rightConst.longValue());
            if (op == LongRangeBinOp.MINUS) {
              value2 = value2.negate(resultType.getLongRangeType());
            }
            LongRangeSet res = value1.plus(value2, resultType.getLongRangeType());
            right = myFactory.fromDfType(resultType.meetRange(res));
            return create(sumValue.getLeft(), right, state, resultType, LongRangeBinOp.PLUS);
          }
        }
        if (op == LongRangeBinOp.MINUS && sumValue.getOperation() == LongRangeBinOp.PLUS) {
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

    /**
     * @param state memory state
     * @param dividend dividend
     * @param divisor divisor
     * @return true if it's known that dividend is within divisor range
     */
    private static boolean withinDivisorRange(@NotNull DfaMemoryState state,
                                              @NotNull DfaValue dividend,
                                              @NotNull DfaValue divisor,
                                              @NotNull LongRangeSet dividendRange,
                                              @NotNull LongRangeSet divisorRange) {
      if (divisorRange.min() > 0) {
        // a % b where 0 <= a < b
        if (dividendRange.min() > -divisorRange.max() &&
            (dividendRange.max() < divisorRange.min() || state.getRelation(dividend, divisor) == RelationType.LT)) {
          return true;
        }
        if (dividend instanceof DfaBinOpValue) {
          LongRangeBinOp prevOp = ((DfaBinOpValue)dividend).getOperation();
          if (prevOp == LongRangeBinOp.MINUS) {
            boolean negative = dividendRange.max() <= 0;
            boolean positive = dividendRange.min() >= 0;
            if (positive || negative) {
              DfaVariableValue left = ((DfaBinOpValue)dividend).getLeft();
              DfaValue right = ((DfaBinOpValue)dividend).getRight();
              DfIntegralType leftType = ObjectUtils.tryCast(state.getDfType(left), DfIntegralType.class);
              DfIntegralType rightType = ObjectUtils.tryCast(state.getDfType(right), DfIntegralType.class);
              if (leftType != null && rightType != null) {
                LongRangeSet leftRange = leftType.getRange();
                LongRangeSet rightRange = rightType.getRange();
                if (leftRange.min() >= 0 && rightRange.min() >= 0) {
                  // (a-b) % c where (a-b)<0 && a>=0 && b>=0 && b<c (or b==c && a>0)
                  if (negative) {
                    RelationType relation = state.getRelation(right, divisor);
                    if (relation == RelationType.LT || relation == RelationType.EQ && leftRange.min() >= 1) {
                      return true;
                    }
                  }
                  // (a-b) % c where (a-b)>0 && a>=0 && b>=0 && a<c (or a==c && b>0)
                  if (positive) {
                    RelationType relation = state.getRelation(left, divisor);
                    if (relation == RelationType.LT || relation == RelationType.EQ && rightRange.min() >= 1) {
                      return true;
                    }
                  }
                }
              }
            }
          }
        }
      }
      return false;
    }

    @NotNull
    private DfaBinOpValue doCreate(DfaVariableValue left, DfaValue right, DfIntegralType resultType, LongRangeBinOp op) {
      long hash = ((long)left.getID() << 32) | right.getID();
      Key key = new Key(hash, op, resultType);
      return myValues.computeIfAbsent(key, k -> new DfaBinOpValue(left, right, resultType, op));
    }
  }
}
