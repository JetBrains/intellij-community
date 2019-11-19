// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaFactMap;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a condition to be applied to DFA memory state. 
 * This interface has only two implementations: {@link Exact} and {@link DfaRelation}.
 * No other implementations allowed.
 */
public abstract class DfaCondition {
  // To prevent inheritors outside of the package
  DfaCondition() {}
  
  @NotNull
  @Contract(pure = true)
  public abstract DfaCondition createNegated();

  /**
   * @return always true condition; singleton object
   */
  @Contract(pure = true)
  public static DfaCondition getTrue() {
    return Exact.TRUE;
  }

  /**
   * @return always false condition; singleton object
   */
  @Contract(pure = true)
  public static DfaCondition getFalse() {
    return Exact.FALSE;
  }

  /**
   * @return condition with unknown value; singleton object
   */
  @Contract(pure = true)
  public static DfaCondition getUnknown() {
    return Exact.UNKNOWN;
  }

  /**
   * @see DfaValue#cond(RelationType, DfaValue) 
   */
  @NotNull
  static DfaCondition createCondition(@NotNull DfaValue left, @NotNull RelationType relationType, @NotNull DfaValue right) {
    Exact value = Exact.tryEvaluate(left, relationType, right);
    if (value != null) return value;
    DfaRelation relation = DfaRelation.createRelation(left, relationType, right);
    if (relation != null) return relation;
    return Exact.UNKNOWN;
  }

  static class Exact extends DfaCondition {
    private final String myName;
  
    private Exact(String name) {
      myName = name;
    }
  
    @Override
    public String toString() {
      return myName;
    }
  
    static final Exact TRUE = new Exact("TRUE");
    static final Exact FALSE = new Exact("FALSE");
    static final Exact UNKNOWN = new Exact("UNKNOWN");
  
    @NotNull
    @Override
    public DfaCondition createNegated() {
      if (this == TRUE) return FALSE;
      if (this == FALSE) return TRUE;
      return UNKNOWN;
    }
    
    private static Exact fromBoolean(boolean value) {
      return value ? TRUE : FALSE;
    }
  
    @Nullable
    static Exact tryEvaluate(DfaValue dfaLeft, RelationType relationType, DfaValue dfaRight) {
      if (DfaConstValue.isSentinel(dfaLeft) != DfaConstValue.isSentinel(dfaRight)) {
        return fromBoolean(relationType == RelationType.NE);
      }
      if (dfaRight instanceof DfaFactMapValue && DfaConstValue.isConstant(dfaLeft, null)) {
        return tryEvaluate(dfaRight, relationType, dfaLeft);
      }
      if (dfaLeft instanceof DfaFactMapValue &&
          DfaConstValue.isConstant(dfaRight, null) &&
          DfaNullability.isNotNull(((DfaFactMapValue)dfaLeft).getFacts())) {
        if (relationType == RelationType.EQ) {
          return FALSE;
        }
        if (relationType == RelationType.NE) {
          return TRUE;
        }
      }
  
      if(dfaLeft instanceof DfaFactMapValue && dfaRight instanceof DfaFactMapValue) {
        if(relationType == RelationType.IS || relationType == RelationType.IS_NOT) {
          DfaFactMap leftFacts = ((DfaFactMapValue)dfaLeft).getFacts();
          DfaFactMap rightFacts = ((DfaFactMapValue)dfaRight).getFacts();
          boolean isSuperState = rightFacts.isSuperStateOf(leftFacts);
          if (isSuperState) {
            return fromBoolean(relationType == RelationType.IS);
          }
          boolean isDistinct = rightFacts.intersect(leftFacts) == null;
          if (isDistinct) {
            return fromBoolean(relationType == RelationType.IS_NOT);
          }
        }
      }
      if (relationType == RelationType.EQ || relationType == RelationType.NE) {
        SpecialField leftSpecialField = SpecialField.fromQualifier(dfaLeft);
        if (leftSpecialField != null) {
          SpecialField rightSpecialField = SpecialField.fromQualifier(dfaRight);
          DfaValueFactory factory = dfaLeft.getFactory();
          if (rightSpecialField == leftSpecialField && factory != null) {
            DfaValue leftValue = leftSpecialField.createValue(factory, dfaLeft);
            DfaValue rightValue = leftSpecialField.createValue(factory, dfaRight);
            Exact
              specialFieldComparison = tryEvaluate(leftValue, RelationType.EQ, rightValue);
            if (specialFieldComparison == FALSE) {
              return fromBoolean(relationType == RelationType.NE);
            }
          }
        }
      }
  
      LongRangeSet leftRange = LongRangeSet.fromDfaValue(dfaLeft);
      LongRangeSet rightRange = LongRangeSet.fromDfaValue(dfaRight);
      if (leftRange != null && rightRange != null) {
        LongRangeSet constraint = rightRange.fromRelation(relationType);
        if (constraint != null && !constraint.intersects(leftRange)) {
          return FALSE;
        }
        LongRangeSet revConstraint = rightRange.fromRelation(relationType.getNegated());
        if (revConstraint != null && !revConstraint.intersects(leftRange)) {
          return TRUE;
        }
      }
  
      if(dfaLeft instanceof DfaConstValue && dfaRight instanceof DfaConstValue &&
         (relationType == RelationType.EQ || relationType == RelationType.NE)) {
        return fromBoolean(dfaLeft == dfaRight ^
                           !DfaUtil.isNaN(((DfaConstValue)dfaLeft).getValue()) ^
                          relationType == RelationType.EQ);
      }
  
      return null;
    }
  }
}
