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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * A condition that represents a relation between two DfaValues
 */
public final class DfaRelation extends DfaCondition {
  @NotNull
  @Override
  public DfaRelation negate() {
    return new DfaRelation(myLeftOperand, myRightOperand, myRelation.getNegated());
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

  @Override
  public boolean isUnknown() {
    return myLeftOperand instanceof DfaTypeValue && myRightOperand instanceof DfaTypeValue;
  }

  public static DfaRelation createRelation(@NotNull DfaValue dfaLeft, @NotNull RelationType relationType, @NotNull DfaValue dfaRight) {
    if (dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaWrappedValue || dfaLeft instanceof DfaBinOpValue
        || dfaRight instanceof DfaVariableValue || dfaRight instanceof DfaWrappedValue || dfaRight instanceof DfaBinOpValue) {
      if (!(dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaWrappedValue || dfaLeft instanceof DfaBinOpValue) ||
          (dfaRight instanceof DfaBinOpValue && !(dfaLeft instanceof DfaBinOpValue))) {
        RelationType flipped = relationType.getFlipped();
        return flipped == null ? null : new DfaRelation(dfaRight, dfaLeft, flipped);
      }
    }
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
