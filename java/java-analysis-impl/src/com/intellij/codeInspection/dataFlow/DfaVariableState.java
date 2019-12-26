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

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DfaVariableState {
  @NotNull final DfType myDfType;
  private final int myHash;

  DfaVariableState(@NotNull DfaVariableValue dfaVar) {
    this(dfaVar.getInherentType());
  }

  DfaVariableState(@NotNull DfType dfType) {
    myDfType = dfType instanceof DfReferenceType ? ((DfReferenceType)dfType).dropSpecialField() : dfType;
    myHash = myDfType.hashCode();
  }

  public boolean isSuperStateOf(DfaVariableState other) {
    return this.myDfType.isMergeable(other.myDfType);
  }

  @NotNull
  DfaVariableState withoutType(@NotNull TypeConstraint type) {
    if (myDfType instanceof DfReferenceType) {
      DfReferenceType dfType = (DfReferenceType)myDfType;
      TypeConstraint constraint = dfType.getConstraint();
      if (constraint.equals(type)) {
        return createCopy(dfType.dropTypeConstraint());
      }
      if (type instanceof TypeConstraint.Exact) {
        TypeConstraint.Exact exact = (TypeConstraint.Exact)type;
        TypeConstraint result = TypeConstraints.TOP;
        result = constraint.instanceOfTypes().without(exact).map(TypeConstraint.Exact::instanceOf)
          .foldLeft(result, TypeConstraint::meet);
        result = constraint.notInstanceOfTypes().without(exact).map(TypeConstraint.Exact::notInstanceOf)
          .foldLeft(result, TypeConstraint::meet);
        return createCopy(dfType.dropTypeConstraint().meet(result.asDfType()));
      }
    }
    return this;
  }

  public int hashCode() {
    return myHash;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaVariableState)) return false;
    DfaVariableState aState = (DfaVariableState) obj;
    return myHash == aState.myHash && myDfType.equals(aState.myDfType);
  }

  @NotNull
  protected DfaVariableState createCopy(@NotNull DfType dfType) {
    return dfType.equals(myDfType) ? this : new DfaVariableState(dfType);
  }

  public String toString() {
    return "State: " + myDfType;
  }

  @NotNull
  Nullability getNullability() {
    return DfaNullability.toNullability(DfaNullability.fromDfType(myDfType));
  }

  public boolean isNotNull() {
    return !myDfType.isSuperType(DfTypes.NULL);
  }

  @NotNull
  DfaVariableState withNotNull() {
    return getNullability() == Nullability.NOT_NULL ? this : withNullability(DfaNullability.UNKNOWN);
  }
  
  @NotNull
  DfaVariableState withNullability(@NotNull DfaNullability nullability) {
    if (myDfType instanceof DfReferenceType && ((DfReferenceType)myDfType).getNullability() != nullability) {
      return createCopy(((DfReferenceType)myDfType).dropNullability().meet(nullability.asDfType()));
    }
    return this;
  }

  @Nullable
  DfaVariableState meet(DfType dfType) {
    DfType result = myDfType.meet(dfType);
    return result == DfTypes.BOTTOM ? null : createCopy(result);
  }

  @NotNull
  public DfaVariableState withValue(DfaValue value) {
    return this;
  }

  @NotNull
  public TypeConstraint getTypeConstraint() {
    return TypeConstraint.fromDfType(myDfType);
  }
}
