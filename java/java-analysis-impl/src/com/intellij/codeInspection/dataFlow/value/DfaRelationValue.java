/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.dataFlow.value;

import com.intellij.openapi.util.Trinity;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DfaRelationValue extends DfaValue {
  @Override
  public DfaRelationValue createNegated() {
    return myFactory.getRelationFactory().createCanonicalRelation(myLeftOperand, myRelation.getNegated(), myRightOperand);
  }

  private @NotNull DfaValue myLeftOperand;
  private @NotNull DfaValue myRightOperand;
  private @NotNull RelationType myRelation;

  public enum RelationType {
    LE("<="), LT("<"), GE(">="), GT(">"), EQ("=="), NE("!="),
    /**
     * Value on the left belongs to the class of values defined on the right.
     * Currently used to represent:
     * - instanceof (DfaValue IS DfaTypeValue)
     * - optional presense (DfaValue IS DfaOptionalValue)
     */
    IS("is"),
    /**
     * Value on the left does not belong to the class of values defined on the right (opposite to IS).
     */
    IS_NOT("isn't");

    private final String myName;

    RelationType(String name) {
      myName = name;
    }

    public boolean isSubRelation(RelationType other) {
      if (other == this) return true;
      switch (this) {
        case LE:
          return other == LT || other == EQ;
        case GE:
          return other == GT || other == EQ;
        case NE:
          return other == LT || other == GT;
        default:
          return false;
      }
    }

    @NotNull
    public RelationType getNegated() {
      switch (this) {
        case LE:
          return GT;
        case LT:
          return GE;
        case GE:
          return LT;
        case GT:
          return LE;
        case EQ:
          return NE;
        case NE:
          return EQ;
        case IS:
          return IS_NOT;
        case IS_NOT:
          return IS;
      }
      throw new InternalError("Unexpected enum value: " + this);
    }

    @Nullable
    public RelationType getFlipped() {
      switch (this) {
        case LE:
          return GE;
        case LT:
          return GT;
        case GE:
          return LE;
        case GT:
          return LT;
        case EQ:
        case NE:
          return this;
        default:
          return null;
      }
    }

    @Override
    public String toString() {
      return myName;
    }

    @Nullable
    public static RelationType fromElementType(IElementType type) {
      if(JavaTokenType.EQEQ.equals(type)) {
        return EQ;
      }
      if(JavaTokenType.NE.equals(type)) {
        return NE;
      }
      if(JavaTokenType.LT.equals(type)) {
        return LT;
      }
      if(JavaTokenType.GT.equals(type)) {
        return GT;
      }
      if(JavaTokenType.LE.equals(type)) {
        return LE;
      }
      if(JavaTokenType.GE.equals(type)) {
        return GE;
      }
      if(JavaTokenType.INSTANCEOF_KEYWORD.equals(type)) {
        return IS;
      }
      return null;
    }

    public static RelationType equivalence(boolean equal) {
      return equal ? EQ : NE;
    }
  }

  private DfaRelationValue(@NotNull DfaValue leftOperand, @NotNull DfaValue rightOperand, @NotNull RelationType relationType,
                           DfaValueFactory factory) {
    super(factory);
    myLeftOperand = leftOperand;
    myRightOperand = rightOperand;
    myRelation = relationType;
  }

  @NotNull
  public DfaValue getLeftOperand() {
    return myLeftOperand;
  }

  @NotNull
  public DfaValue getRightOperand() {
    return myRightOperand;
  }

  public static class Factory {
    private final Map<Trinity<DfaValue, DfaValue, RelationType>, DfaRelationValue> myValues;
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      myValues = new HashMap<>();
    }

    public DfaRelationValue createRelation(DfaValue dfaLeft, RelationType relationType, DfaValue dfaRight) {
      if ((relationType == RelationType.IS || relationType == RelationType.IS_NOT) &&
          dfaRight instanceof DfaFactMapValue && !(dfaLeft instanceof DfaFactMapValue)) {
        return createCanonicalRelation(dfaLeft, relationType, dfaRight);
      }
      if (dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaUnboxedValue
          || dfaRight instanceof DfaVariableValue || dfaRight instanceof DfaBoxedValue || dfaRight instanceof DfaUnboxedValue) {
        if (!(dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaUnboxedValue)) {
          RelationType flipped = relationType.getFlipped();
          return flipped == null ? null : createCanonicalRelation(dfaRight, flipped, dfaLeft);
        }
        return createCanonicalRelation(dfaLeft, relationType, dfaRight);
      }
      if (dfaLeft instanceof DfaFactMapValue && dfaRight instanceof DfaConstValue) {
        return createCanonicalRelation(DfaUnknownValue.getInstance(), relationType, dfaRight);
      }
      else if (dfaRight instanceof DfaFactMapValue && dfaLeft instanceof DfaConstValue) {
        return createCanonicalRelation(DfaUnknownValue.getInstance(), relationType, dfaLeft);
      }
      return null;
    }

    @NotNull
    private DfaRelationValue createCanonicalRelation(@NotNull final DfaValue dfaLeft,
                                                     @NotNull RelationType relationType,
                                                     @NotNull final DfaValue dfaRight) {
      return myValues.computeIfAbsent(Trinity.create(dfaLeft, dfaRight, relationType),
                                      k -> new DfaRelationValue(dfaLeft, dfaRight, relationType, myFactory));
    }
  }

  public boolean isEquality() {
    return myRelation == RelationType.EQ;
  }

  public boolean isNonEquality() {
    return myRelation == RelationType.NE || myRelation == RelationType.GT || myRelation == RelationType.LT;
  }

  @NotNull
  public RelationType getRelation() {
    return myRelation;
  }

  @NonNls public String toString() {
    return myLeftOperand + " " + myRelation + " " + myRightOperand;
  }
}
