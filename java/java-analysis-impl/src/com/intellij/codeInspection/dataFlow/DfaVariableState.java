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

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class DfaVariableState {
  @NotNull final Set<DfaPsiType> myInstanceofValues;
  @NotNull final Set<DfaPsiType> myNotInstanceofValues;
  @NotNull final Nullness myNullability;
  @NotNull final DfaFactMap myFactMap;
  private final int myHash;

  DfaVariableState(@NotNull DfaVariableValue dfaVar) {
    this(Collections.emptySet(), Collections.emptySet(), dfaVar.getInherentNullability(),
         DfaFactMap.EMPTY.with(DfaFactType.RANGE, getInitialRange(dfaVar)));
  }

  public boolean isSuperStateOf(DfaVariableState that) {
    if(!myNotInstanceofValues.equals(that.myNotInstanceofValues)) return false;
    if(!myInstanceofValues.equals(that.myNotInstanceofValues)) return false;
    if(!myNullability.equals(that.myNullability)) return false;
    return myFactMap.isSuperStateOf(that.myFactMap);
  }

  private static LongRangeSet getInitialRange(DfaVariableValue var) {
    DfaVariableValue qualifier = var.getQualifier();
    if(qualifier != null) {
      PsiModifierListOwner owner = var.getPsiVariable();
      for (SpecialField sf : SpecialField.values()) {
        if(sf.isMyAccessor(owner)) {
          return sf.getRange();
        }
      }
    }
    return LongRangeSet.fromType(var.getVariableType());
  }

  DfaVariableState(@NotNull Set<DfaPsiType> instanceofValues,
                   @NotNull Set<DfaPsiType> notInstanceofValues,
                   @NotNull Nullness nullability,
                   @NotNull DfaFactMap factMap) {
    myInstanceofValues = instanceofValues;
    myNotInstanceofValues = notInstanceofValues;
    myNullability = nullability;
    myFactMap = factMap;
    myHash = Objects.hash(myInstanceofValues, myNotInstanceofValues, myNullability, myFactMap);
  }

  public boolean isNullable() {
    return myNullability == Nullness.NULLABLE;
  }

  private boolean checkInstanceofValue(@NotNull DfaPsiType dfaType) {
    if (myInstanceofValues.contains(dfaType)) return true;

    for (DfaPsiType dfaTypeValue : myNotInstanceofValues) {
      if (dfaTypeValue.isAssignableFrom(dfaType)) return false;
    }

    for (DfaPsiType dfaTypeValue : myInstanceofValues) {
      if (!dfaType.isConvertibleFrom(dfaTypeValue)) return false;
    }

    return true;
  }

  @Nullable
  DfaVariableState withInstanceofValue(@NotNull DfaTypeValue dfaType) {
    if (dfaType.getDfaType().getPsiType() instanceof PsiPrimitiveType) return this;

    if (checkInstanceofValue(dfaType.getDfaType())) {
      DfaVariableState result = dfaType.isNullable() ? withNullability(Nullness.NULLABLE) : this;
      List<DfaPsiType> moreGeneric = ContainerUtil.newArrayList();
      for (DfaPsiType alreadyInstanceof : myInstanceofValues) {
        if (dfaType.getDfaType().isAssignableFrom(alreadyInstanceof)) {
          return result;
        }
        if (alreadyInstanceof.isAssignableFrom(dfaType.getDfaType())) {
          moreGeneric.add(alreadyInstanceof);
        }
      }

      HashSet<DfaPsiType> newInstanceof = ContainerUtil.newHashSet(myInstanceofValues);
      newInstanceof.removeAll(moreGeneric);
      newInstanceof.add(dfaType.getDfaType());
      result = createCopy(newInstanceof, myNotInstanceofValues, result.myNullability, myFactMap);
      return result;
    }

    return null;
  }

  @Nullable
  DfaVariableState withNotInstanceofValue(@NotNull DfaTypeValue dfaType) {
    if (myNotInstanceofValues.contains(dfaType.getDfaType())) return this;

    for (DfaPsiType dfaTypeValue : myInstanceofValues) {
      if (dfaType.getDfaType().isAssignableFrom(dfaTypeValue)) return null;
    }

    List<DfaPsiType> moreSpecific = ContainerUtil.newArrayList();
    for (DfaPsiType alreadyNotInstanceof : myNotInstanceofValues) {
      if (alreadyNotInstanceof.isAssignableFrom(dfaType.getDfaType())) {
        return this;
      }
      if (dfaType.getDfaType().isAssignableFrom(alreadyNotInstanceof)) {
        moreSpecific.add(alreadyNotInstanceof);
      }
    }

    HashSet<DfaPsiType> newNotInstanceof = ContainerUtil.newHashSet(myNotInstanceofValues);
    newNotInstanceof.removeAll(moreSpecific);
    newNotInstanceof.add(dfaType.getDfaType());
    return createCopy(myInstanceofValues, newNotInstanceof, myNullability, myFactMap);
  }

  @NotNull
  DfaVariableState withoutType(@NotNull DfaPsiType type) {
    if (myInstanceofValues.contains(type)) {
      HashSet<DfaPsiType> newInstanceof = ContainerUtil.newHashSet(myInstanceofValues);
      newInstanceof.remove(type);
      return createCopy(newInstanceof, myNotInstanceofValues, myNullability, myFactMap);
    }
    if (myNotInstanceofValues.contains(type)) {
      HashSet<DfaPsiType> newNotInstanceof = ContainerUtil.newHashSet(myNotInstanceofValues);
      newNotInstanceof.remove(type);
      return createCopy(myInstanceofValues, newNotInstanceof, myNullability, myFactMap);
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
    return myHash == aState.myHash &&
           myNullability == aState.myNullability &&
           myInstanceofValues.equals(aState.myInstanceofValues) &&
           myNotInstanceofValues.equals(aState.myNotInstanceofValues) &&
           Objects.equals(myFactMap, aState.myFactMap);
  }

  @NotNull
  protected DfaVariableState createCopy(@NotNull Set<DfaPsiType> instanceofValues,
                                        @NotNull Set<DfaPsiType> notInstanceofValues,
                                        @NotNull Nullness nullability,
                                        @NotNull DfaFactMap fact) {
    return new DfaVariableState(instanceofValues, notInstanceofValues, nullability, fact);
  }

  public String toString() {
    @NonNls StringBuilder buf = new StringBuilder();

    buf.append(myNullability);
    if (!myInstanceofValues.isEmpty()) {
      buf.append(" instanceof ").append(StringUtil.join(myInstanceofValues, ","));
    }

    if (!myNotInstanceofValues.isEmpty()) {
      buf.append(" not instanceof ").append(StringUtil.join(myNotInstanceofValues, ","));
    }

    String factString = myFactMap.toString();
    if(!factString.isEmpty()) {
      buf.append(" ").append(factString);
    }
    return buf.toString();
  }

  @NotNull
  Nullness getNullability() {
    return myNullability;
  }

  public boolean isNotNull() {
    return myNullability == Nullness.NOT_NULL;
  }

  @NotNull
  DfaVariableState withNullability(@NotNull Nullness nullness) {
    return myNullability == nullness ? this : createCopy(myInstanceofValues, myNotInstanceofValues, nullness, myFactMap);
  }

  @NotNull
  DfaVariableState withNullable(final boolean nullable) {
    return myNullability != Nullness.NOT_NULL ? withNullability(nullable ? Nullness.NULLABLE : Nullness.UNKNOWN) : this;
  }

  @NotNull
  <T> DfaVariableState withFact(DfaFactType<T> type, T value) {
    DfaFactMap factMap = myFactMap.with(type, value);
    return myFactMap.equals(factMap) ? this : createCopy(myInstanceofValues, myNotInstanceofValues, myNullability, factMap);
  }

  @Nullable
  <T> DfaVariableState intersectFact(DfaFactType<T> type, T value) {
    DfaFactMap factMap = myFactMap.intersect(type, value);
    return factMap == null
           ? null
           : myFactMap.equals(factMap) ? this : createCopy(myInstanceofValues, myNotInstanceofValues, myNullability, factMap);
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
  public Set<DfaPsiType> getInstanceofValues() {
    return myInstanceofValues;
  }

  @NotNull
  public Set<DfaPsiType> getNotInstanceofValues() {
    return myNotInstanceofValues;
  }

  @Nullable
  public <T> T getFact(@NotNull DfaFactType<T> factType) {
    return myFactMap.get(factType);
  }
}
