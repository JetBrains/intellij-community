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
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DfaRelationValue extends DfaValue {
  @Override
  public DfaRelationValue createNegated() {
    return myFactory.getRelationFactory().createCanonicalRelation(myLeftOperand, myRelation.getNegated(), myRightOperand);
  }

  private @NotNull final DfaValue myLeftOperand;
  private @NotNull final DfaValue myRightOperand;
  private @NotNull final RelationType myRelation;

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

    public DfaRelationValue createRelation(@NotNull DfaValue dfaLeft, @NotNull RelationType relationType, @NotNull DfaValue dfaRight) {
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
      return null;
    }

    @NotNull
    private DfaRelationValue createConstBasedRelation(DfaFactMapValue dfaLeft, RelationType relationType, DfaConstValue dfaRight) {
      if (dfaRight.getValue() == null && DfaNullability.isNullable(dfaLeft.getFacts())) {
        return createCanonicalRelation(myFactory.getFactValue(DfaFactType.NULLABILITY, DfaNullability.NULLABLE), relationType, dfaRight);
      }
      return createCanonicalRelation(DfaUnknownValue.getInstance(), relationType, dfaRight);
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

  @Nullable
  @Override
  public PsiType getType() {
    return PsiType.BOOLEAN;
  }

  @NonNls public String toString() {
    return myLeftOperand + " " + myRelation + " " + myRightOperand;
  }
}
