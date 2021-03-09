// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
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
  private final boolean myLong;
  private final @NotNull LongRangeBinOp myOp;

  private DfaBinOpValue(@NotNull DfaVariableValue left, @NotNull DfaValue right, boolean isLong, @NotNull LongRangeBinOp op) {
    super(left.getFactory());
    switch (op) {
      case PLUS:
        if (!(right.getDfType() instanceof DfConstantType) && !(right instanceof DfaVariableValue)) {
          throw new IllegalArgumentException("RHO must be constant or variable for plus");
        }
        break;
      case MINUS:
        if (!(right instanceof DfaVariableValue)) {
          throw new IllegalArgumentException("RHO must be variable for minus");
        }
        break;
      case MOD:
        if (!(right.getDfType() instanceof DfConstantType)) {
          throw new IllegalArgumentException("RHO must be constant for mod");
        }
        break;
      default:
        throw new IllegalArgumentException("Unsupported op: " + op);
    }
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

  @Override
  public @NotNull PsiType getType() {
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

  @NotNull
  public DfaValue tryReduceOnCast(DfaMemoryState state, PsiPrimitiveType type) {
    if (!TypeConversionUtil.isIntegralNumberType(type)) return this;
    if ((myOp == LongRangeBinOp.PLUS || myOp == LongRangeBinOp.MINUS) &&
        DfLongType.extractRange(state.getDfType(myRight)).castTo(type).equals(LongRangeSet.point(0))) {
      return myLeft;
    }
    if (myOp == LongRangeBinOp.PLUS &&
        DfLongType.extractRange(state.getDfType(myLeft)).castTo(type).equals(LongRangeSet.point(0))) {
      return myRight;
    }
    return this;
  }

  private static long extractLong(DfaTypeValue right) {
    return Objects.requireNonNull(right.getDfType().getConstantOfType(Number.class)).longValue();
  }

  public static class Factory {
    private final DfaValueFactory myFactory;
    private final Map<Pair<Long, LongRangeBinOp>, DfaBinOpValue> myValues = new HashMap<>();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaValue create(DfaValue left, DfaValue right, DfaMemoryState state, boolean isLong, IElementType tokenType) {
      LongRangeBinOp op = LongRangeBinOp.fromToken(tokenType);
      if (op == null) return myFactory.getUnknown();
      DfaValue value = doCreate(left, right, state, isLong, op);
      if (value != null) {
        return value;
      }
      DfIntegralType leftType = ObjectUtils.tryCast(state.getDfType(left), DfIntegralType.class);
      DfIntegralType rightType = ObjectUtils.tryCast(state.getDfType(right), DfIntegralType.class);
      if (leftType == null || rightType == null) {
        return myFactory.fromDfType(isLong ? DfTypes.LONG : DfTypes.INT);
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
    private DfaValue doCreate(DfaValue left, DfaValue right, DfaMemoryState state, boolean isLong, LongRangeBinOp op) {
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
        return myFactory.getInt(0);
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
            return doCreate((DfaVariableValue)left, right, isLong, op);
          }
        }
        return null;
      }
      if (leftConst != null && (right instanceof DfaVariableValue || right instanceof DfaBinOpValue) && op == LongRangeBinOp.PLUS) {
        return doCreate(right, left, state, isLong, op);
      }
      if (left instanceof DfaVariableValue) {
        if (right instanceof DfaVariableValue) {
          if (op == LongRangeBinOp.PLUS && right.getID() > left.getID()) {
            return doCreate((DfaVariableValue)right, left, isLong, op);
          }
          return doCreate((DfaVariableValue)left, right, isLong, op);
        }
        if (rightConst != null) {
          long value = rightConst.longValue();
          if (value == 0) return left;
          if (op == LongRangeBinOp.MINUS) {
            right = myFactory.fromDfType(isLong ? DfTypes.longValue(-value) : DfTypes.intValue(-(int)value));
          }
          return doCreate((DfaVariableValue)left, right, isLong, LongRangeBinOp.PLUS);
        }
      }
      if (left instanceof DfaBinOpValue) {
        DfaBinOpValue sumValue = (DfaBinOpValue)left;
        if (sumValue.getOperation() != LongRangeBinOp.PLUS && sumValue.getOperation() != LongRangeBinOp.MINUS) return null;
        if (rightConst != null) {
          if (sumValue.getRight() instanceof DfaTypeValue) {
            long value1 = extractLong((DfaTypeValue)sumValue.getRight());
            long value2 = rightConst.longValue();
            if (op == LongRangeBinOp.MINUS) {
              value2 = -value2;
            }
            long res = value1 + value2;
            right = myFactory.fromDfType(isLong ? DfTypes.longValue(res) : DfTypes.intValue((int)res));
            return create(sumValue.getLeft(), right, state, isLong, JavaTokenType.PLUS);
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
     * @param dividendRange
     * @param divisorRange
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
    private DfaBinOpValue doCreate(DfaVariableValue left, DfaValue right, boolean isLong, LongRangeBinOp op) {
      long hash = ((isLong ? 1L : 0L) << 63) | ((long)left.getID() << 32) | right.getID();
      Pair<Long, LongRangeBinOp> key = Pair.create(hash, op);
      return myValues.computeIfAbsent(key, k -> new DfaBinOpValue(left, right, isLong, op));
    }
  }
}
