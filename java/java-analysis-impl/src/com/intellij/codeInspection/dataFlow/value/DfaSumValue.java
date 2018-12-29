// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtils;
import gnu.trove.TLongObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a value like "variable+/-constant"
 */
public class DfaSumValue extends DfaValue {
  private final @NotNull DfaVariableValue myLeft;
  private final @NotNull DfaValue myRight;
  private final boolean myLong;
  private final boolean myNegation;

  private DfaSumValue(@NotNull DfaVariableValue left, @NotNull DfaValue right, boolean isLong, boolean negation) {
    super(left.getFactory());
    assert (right instanceof DfaConstValue && !negation) || right instanceof DfaVariableValue;
    myLeft = left;
    myRight = right;
    myLong = isLong;
    myNegation = negation;
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

  public boolean isNegation() {
    return myNegation;
  }

  @Override
  public String toString() {
    String delimiter;
    if (myRight instanceof DfaConstValue &&
        ((DfaConstValue)myRight).getValue() instanceof Long &&
        (Long)((DfaConstValue)myRight).getValue() < 0) {
      delimiter = "";
    } else {
      delimiter = myNegation ? "-" : "+";
    }
    return myLeft + delimiter + myRight;
  }

  @NotNull
  public IElementType getTokenType() {
    return isNegation() ? JavaTokenType.MINUS : JavaTokenType.PLUS;
  }

  public static class Factory {
    private final DfaValueFactory myFactory;
    private final TLongObjectHashMap<DfaSumValue> myValues = new TLongObjectHashMap<>();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaValue create(DfaValue left, DfaValue right, DfaMemoryState state, boolean isLong, boolean negation) {
      DfaConstValue leftConst = state.getConstantValue(left);
      if (leftConst != null) {
        left = leftConst;
      }
      DfaConstValue rightConst = state.getConstantValue(right);
      if (rightConst != null) {
        right = rightConst;
      }
      if (negation && state.areEqual(left, right)) {
        return myFactory.getInt(0);
      }
      if (left instanceof DfaConstValue && (right instanceof DfaVariableValue || right instanceof DfaSumValue) && !negation) {
        return create(right, left, state, isLong, false);
      }
      if (left instanceof DfaVariableValue) {
        if (right instanceof DfaVariableValue) {
          if (!negation && right.getID() > left.getID()) {
            return doCreate((DfaVariableValue)right, left, isLong, false);
          }
          return doCreate((DfaVariableValue)left, right, isLong, negation);
        }
        if (right instanceof DfaConstValue) {
          Long value = ObjectUtils.tryCast(((DfaConstValue)right).getValue(), Long.class);
          if (value != null) {
            if (value == 0) return left;
            if (negation && (isLong || value != Integer.MIN_VALUE)) {
              right = myFactory.getConstFactory().createFromValue(-value, PsiType.LONG);
            }
            return doCreate((DfaVariableValue)left, right, isLong, false);
          }
        }
      }
      if (left instanceof DfaSumValue) {
        DfaSumValue sumValue = (DfaSumValue)left;
        if (right instanceof DfaConstValue) {
          if (sumValue.getRight() instanceof DfaConstValue) {
            Long value1 = ObjectUtils.tryCast(((DfaConstValue)sumValue.getRight()).getValue(), Long.class);
            Long value2 = ObjectUtils.tryCast(((DfaConstValue)right).getValue(), Long.class);
            if (value1 != null && value2 != null) {
              if (negation) {
                value2 = -value2;
              }
              long res = value1 + value2;
              right = myFactory.getConstFactory().createFromValue(isLong ? res : (int)res, PsiType.LONG);
              return create(sumValue.getLeft(), right, state, isLong, false);
            }
          }
        }
        if (negation && !sumValue.isNegation()) {
          // a+b-a => b; a+b-b => a 
          if (state.areEqual(right, sumValue.getLeft())) {
            return sumValue.getRight();
          }
          else if (state.areEqual(right, sumValue.getRight())) {
            return sumValue.getLeft();
          }
        }
      }
      LongRangeSet leftRange = state.getValueFact(left, DfaFactType.RANGE);
      LongRangeSet rightRange = state.getValueFact(right, DfaFactType.RANGE);
      if (leftRange != null && rightRange != null) {
        LongRangeSet result = leftRange.binOpFromToken(negation ? JavaTokenType.MINUS : JavaTokenType.PLUS, rightRange, isLong);
        return myFactory.getFactValue(DfaFactType.RANGE, result);
      }
      return DfaUnknownValue.getInstance();
    }

    @NotNull
    private DfaSumValue doCreate(DfaVariableValue left, DfaValue right, boolean isLong, boolean negation) {
      long hash = ((isLong ? 1L : 0L) << 63) | ((long)left.getID() << 32) | ((negation ? 1L : 0L) << 31) | right.getID();
      DfaSumValue value = myValues.get(hash);
      if (value == null) {
        value = new DfaSumValue(left, right, isLong, negation);
        myValues.put(hash, value);
      }
      return value;
    }
  }
}
