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

import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * A condition that represents a relation between two DfaValues
 */
public final class DfaRelation extends DfaCondition {
  @NotNull
  @Override
  public DfaRelation createNegated() {
    return createCanonicalRelation(myLeftOperand, myRelation.getNegated(), myRightOperand);
  }

  private @NotNull final DfaValue myLeftOperand;
  private @NotNull final DfaValue myRightOperand;
  private @NotNull final RelationType myRelation;

  private DfaRelation(@NotNull DfaValue leftOperand, @NotNull DfaValue rightOperand, @NotNull RelationType relationType) {
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

  public static DfaRelation createRelation(@NotNull DfaValue dfaLeft, @NotNull RelationType relationType, @NotNull DfaValue dfaRight) {
    if ((relationType == RelationType.IS || relationType == RelationType.IS_NOT) &&
        dfaRight instanceof DfaFactMapValue && !(dfaLeft instanceof DfaFactMapValue)) {
      return createCanonicalRelation(dfaLeft, relationType, dfaRight);
    }
    if (dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaBinOpValue
        || dfaRight instanceof DfaVariableValue || dfaRight instanceof DfaBoxedValue || dfaRight instanceof DfaBinOpValue) {
      if (!(dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaBinOpValue) ||
          (dfaRight instanceof DfaBinOpValue && !(dfaLeft instanceof DfaBinOpValue))) {
        RelationType flipped = relationType.getFlipped();
        return flipped == null ? null : createCanonicalRelation(dfaRight, flipped, dfaLeft);
      }
      return createCanonicalRelation(dfaLeft, relationType, dfaRight);
    }
    if (dfaLeft instanceof DfaFactMapValue && dfaRight instanceof DfaConstValue) {
      return createConstBasedRelation((DfaFactMapValue)dfaLeft, relationType, (DfaConstValue)dfaRight);
    }
    else if (dfaRight instanceof DfaFactMapValue && dfaLeft instanceof DfaConstValue) {
      return createConstBasedRelation((DfaFactMapValue)dfaRight, relationType, (DfaConstValue)dfaLeft);
    }
    if (dfaLeft instanceof DfaInstanceofValue && dfaRight instanceof DfaConstValue) {
      return createCanonicalRelation(dfaLeft, relationType, dfaRight);
    }
    if (dfaLeft instanceof DfaConstValue && dfaRight instanceof DfaInstanceofValue) {
      return createCanonicalRelation(dfaRight, relationType, dfaLeft);
    }
    return null;
  }

  @NotNull
  private static DfaRelation createConstBasedRelation(DfaFactMapValue dfaLeft, RelationType relationType, DfaConstValue dfaRight) {
    if (dfaRight.getValue() == null && DfaNullability.isNullable(dfaLeft.getFacts())) {
      return createCanonicalRelation(dfaLeft.getFactory().getFactValue(DfaFactType.NULLABILITY, DfaNullability.NULLABLE), relationType,
                                     dfaRight);
    }
    return createCanonicalRelation(DfaUnknownValue.getInstance(), relationType, dfaRight);
  }

  @NotNull
  private static DfaRelation createCanonicalRelation(@NotNull final DfaValue dfaLeft,
                                                     @NotNull RelationType relationType,
                                                     @NotNull final DfaValue dfaRight) {
    return new DfaRelation(dfaLeft, dfaRight, relationType);
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DfaRelation relation = (DfaRelation)o;
    return myLeftOperand == relation.myLeftOperand &&
           myRightOperand == relation.myRightOperand &&
           myRelation == relation.myRelation;
  }

  @Override
  public int hashCode() {
    int result = 31 + myLeftOperand.hashCode();
    result = 31 * result + myRightOperand.hashCode();
    result = 31 * result + myRelation.hashCode();
    return result;
  }

  @NonNls public String toString() {
    return myLeftOperand + " " + myRelation + " " + myRightOperand;
  }
}
