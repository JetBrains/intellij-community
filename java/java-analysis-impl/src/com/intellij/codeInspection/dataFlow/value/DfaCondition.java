// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.types.*;
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

  /**
   * @return a condition which is the opposite to this condition
   */
  @NotNull
  @Contract(pure = true)
  public abstract DfaCondition negate();

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
    public DfaCondition negate() {
      if (this == TRUE) return FALSE;
      if (this == FALSE) return TRUE;
      return UNKNOWN;
    }
    
    private static Exact fromBoolean(boolean value) {
      return value ? TRUE : FALSE;
    }
  
    @Nullable
    static Exact tryEvaluate(DfaValue dfaLeft, RelationType relationType, DfaValue dfaRight) {
      DfaValue sentinel = dfaLeft.getFactory().getSentinel();
      if ((dfaLeft == sentinel) || (dfaRight == sentinel)) {
        return fromBoolean((dfaLeft == sentinel && dfaRight == sentinel) == (relationType == RelationType.EQ));
      }
      DfType leftType = dfaLeft.getDfType();
      DfType rightType = dfaRight.getDfType();
      
      if (relationType == RelationType.EQ || relationType == RelationType.NE) {
        if (leftType instanceof DfConstantType) {
          if (rightType instanceof DfConstantType) {
            return fromBoolean(leftType.equals(rightType) ^
                               !DfaUtil.isNaN(((DfConstantType<?>)leftType).getValue()) ^
                               relationType == RelationType.EQ);
          }
          if (!couldBeEqualToConstant(rightType, leftType)) {
            return fromBoolean(relationType == RelationType.NE);
          }
        }
        else if (rightType instanceof DfConstantType) {
          if (!couldBeEqualToConstant(leftType, rightType)) {
            return fromBoolean(relationType == RelationType.NE);
          }
        }
        else if (leftType.meet(rightType) == DfTypes.BOTTOM) {
          return fromBoolean(relationType == RelationType.NE);
        }
      }
      if(relationType == RelationType.IS || relationType == RelationType.IS_NOT) {
        boolean isSuperState = rightType.isSuperType(leftType);
        if (isSuperState) {
          return fromBoolean(relationType == RelationType.IS);
        }
        boolean isDistinct = rightType.meet(leftType) == DfTypes.BOTTOM;
        if (isDistinct) {
          return fromBoolean(relationType == RelationType.IS_NOT);
        }
      }

      if (leftType instanceof DfIntegralType && rightType instanceof DfIntegralType) {
        if (((DfIntegralType)leftType).meetRelation(relationType, rightType) == DfTypes.BOTTOM) {
          return FALSE;
        }
        if (((DfIntegralType)leftType).meetRelation(relationType.getNegated(), rightType) == DfTypes.BOTTOM) {
          return TRUE;
        }
      }
  
      return null;
    }

    private static boolean couldBeEqualToConstant(DfType type, DfType constantType) {
      return (type instanceof DfReferenceType ? ((DfReferenceType)type).dropTypeConstraint() : type).isSuperType(constantType);
    }
  }
}
