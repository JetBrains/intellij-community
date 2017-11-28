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

import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class DfaVariableState {
  @NotNull final DfaFactMap myFactMap;
  private final int myHash;

  DfaVariableState(@NotNull DfaVariableValue dfaVar) {
    this(dfaVar.getInherentFacts());
  }

  public boolean isSuperStateOf(DfaVariableState that) {
    return myFactMap.isSuperStateOf(that.myFactMap);
  }

  DfaVariableState(@NotNull DfaFactMap factMap) {
    myFactMap = factMap;
    myHash = myFactMap.hashCode();
  }

  @Nullable
  DfaVariableState withInstanceofValue(@NotNull DfaTypeValue dfaType) {
    if (dfaType.getDfaType().getPsiType() instanceof PsiPrimitiveType) return this;
    TypeConstraint typeConstraint = getTypeConstraint();
    TypeConstraint newTypeConstraint = typeConstraint.withInstanceofValue(dfaType);
    if (newTypeConstraint == null) return null;
    DfaVariableState result = dfaType.isNullable() ? withFact(DfaFactType.CAN_BE_NULL, true) : this;
    return result.withFact(DfaFactType.TYPE_CONSTRAINT, newTypeConstraint);
  }

  @Nullable
  DfaVariableState withNotInstanceofValue(@NotNull DfaTypeValue dfaType) {
    TypeConstraint typeConstraint = getTypeConstraint();
    TypeConstraint newTypeConstraint = typeConstraint.withNotInstanceofValue(dfaType);
    return newTypeConstraint == null ? null : withFact(DfaFactType.TYPE_CONSTRAINT, newTypeConstraint);
  }

  @NotNull
  DfaVariableState withoutType(@NotNull DfaPsiType type) {
    return withFact(DfaFactType.TYPE_CONSTRAINT, getTypeConstraint().withoutType(type));
  }

  public int hashCode() {
    return myHash;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaVariableState)) return false;
    DfaVariableState aState = (DfaVariableState) obj;
    return myHash == aState.myHash && Objects.equals(myFactMap, aState.myFactMap);
  }

  @NotNull
  protected DfaVariableState createCopy(@NotNull DfaFactMap factMap) {
    return new DfaVariableState(factMap);
  }

  public String toString() {
    return "State: " + myFactMap;
  }

  @NotNull
  Nullness getNullability() {
    return NullnessUtil.fromBoolean(myFactMap.get(DfaFactType.CAN_BE_NULL));
  }

  public boolean isNotNull() {
    return getNullability() == Nullness.NOT_NULL;
  }

  @NotNull
  DfaVariableState withNotNull() {
    return getNullability() == Nullness.NOT_NULL ? this : withoutFact(DfaFactType.CAN_BE_NULL);
  }

  @NotNull
  <T> DfaVariableState withFact(DfaFactType<T> type, T value) {
    DfaFactMap factMap = myFactMap.with(type, value);
    return myFactMap.equals(factMap) ? this : createCopy(factMap);
  }

  <T> DfaVariableState withoutFact(DfaFactType<T> type) {
    return withFact(type, null);
  }

  @Nullable
  <T> DfaVariableState intersectFact(DfaFactType<T> type, T value) {
    DfaFactMap factMap = myFactMap.intersect(type, value);
    return factMap == null ? null : myFactMap.equals(factMap) ? this : createCopy(factMap);
  }

  @NotNull
  public DfaVariableState withValue(DfaValue value) {
    return this;
  }

  @Nullable
  public DfaValue getValue() {
    return null;
  }

  @NotNull
  public TypeConstraint getTypeConstraint() {
    TypeConstraint fact = getFact(DfaFactType.TYPE_CONSTRAINT);
    return fact == null ? TypeConstraint.EMPTY : fact;
  }

  @Nullable
  public <T> T getFact(@NotNull DfaFactType<T> factType) {
    return myFactMap.get(factType);
  }
}
