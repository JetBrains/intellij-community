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
public record DfaRelation(@NotNull DfaValue leftOperand, @NotNull DfaValue rightOperand, @NotNull RelationType relationType) implements DfaCondition {
  @NotNull
  @Override
  public DfaRelation negate() {
    return new DfaRelation(leftOperand, rightOperand, relationType.getNegated());
  }

  @Override
  public DfaCondition correctForRelationResult(boolean result) {
    DfaValue newLeft = leftOperand;
    DfaValue newRight = rightOperand;
    if (newLeft instanceof DfaTypeValue leftTypeValue) {
      newLeft = newLeft.getFactory().fromDfType(leftTypeValue.getDfType().correctForRelationResult(relationType, result));
    }
    if (newRight instanceof DfaTypeValue rightTypeValue) {
      newRight = newRight.getFactory().fromDfType(rightTypeValue.getDfType().correctForRelationResult(relationType, result));
    }
    return newLeft == leftOperand && newRight == rightOperand ? this :
           DfaCondition.createCondition(newLeft, relationType, newRight);
  }
  
  @Override
  public boolean isUnknown() {
    return leftOperand instanceof DfaTypeValue && rightOperand instanceof DfaTypeValue;
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
    return relationType == RelationType.EQ;
  }

  @NonNls public String toString() {
    return leftOperand + " " + relationType + " " + rightOperand;
  }
}
