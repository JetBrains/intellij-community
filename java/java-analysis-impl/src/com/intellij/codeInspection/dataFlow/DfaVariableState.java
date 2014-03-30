/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 3, 2002
 * Time: 9:49:29 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DfaVariableState {
  protected final Set<DfaPsiType> myInstanceofValues;
  protected final Set<DfaPsiType> myNotInstanceofValues;
  protected final Nullness myNullability;

  public DfaVariableState(@NotNull DfaVariableValue dfaVar) {
    this(Collections.<DfaPsiType>emptySet(), Collections.<DfaPsiType>emptySet(), dfaVar.getInherentNullability());
  }

  protected DfaVariableState(Set<DfaPsiType> instanceofValues,
                          Set<DfaPsiType> notInstanceofValues, Nullness nullability) {
    myInstanceofValues = instanceofValues;
    myNotInstanceofValues = notInstanceofValues;
    myNullability = nullability;
  }

  public boolean isNullable() {
    return myNullability == Nullness.NULLABLE;
  }

  private boolean checkInstanceofValue(DfaPsiType dfaType) {
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
  public DfaVariableState withInstanceofValue(DfaTypeValue dfaType) {
    if (dfaType.getDfaType().getPsiType() instanceof PsiPrimitiveType) return this;
    
    if (checkInstanceofValue(dfaType.getDfaType())) {
      DfaVariableState result = dfaType.isNullable() ? withNullability(Nullness.NULLABLE) : this;
      if (!myInstanceofValues.contains(dfaType.getDfaType())) {
        HashSet<DfaPsiType> newInstanceof = ContainerUtil.newHashSet(myInstanceofValues);
        newInstanceof.add(dfaType.getDfaType());
        result = createCopy(newInstanceof, myNotInstanceofValues, result.myNullability);
      }
      return result;
    }

    return null;
  }

  @Nullable
  public DfaVariableState withNotInstanceofValue(DfaTypeValue dfaType) {
    if (myNotInstanceofValues.contains(dfaType.getDfaType())) return this;

    for (DfaPsiType dfaTypeValue : myInstanceofValues) {
      if (dfaType.getDfaType().isAssignableFrom(dfaTypeValue)) return null;
    }

    HashSet<DfaPsiType> newNotInstanceof = ContainerUtil.newHashSet(myNotInstanceofValues);
    newNotInstanceof.add(dfaType.getDfaType());
    return createCopy(myInstanceofValues, newNotInstanceof, myNullability);
  }

  DfaVariableState withoutType(DfaPsiType type) {
    if (myInstanceofValues.contains(type)) {
      HashSet<DfaPsiType> newInstanceof = ContainerUtil.newHashSet(myInstanceofValues);
      newInstanceof.remove(type);
      return createCopy(newInstanceof, myNotInstanceofValues, myNullability);
    }
    if (myNotInstanceofValues.contains(type)) {
      HashSet<DfaPsiType> newNotInstanceof = ContainerUtil.newHashSet(myNotInstanceofValues);
      newNotInstanceof.remove(type);
      return createCopy(myInstanceofValues, newNotInstanceof, myNullability);
    }
    return this;
  }

  public int hashCode() {
    return (myInstanceofValues.hashCode() * 31 + myNotInstanceofValues.hashCode()) * 31 + myNullability.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaVariableState)) return false;
    DfaVariableState aState = (DfaVariableState) obj;
    return myNullability == aState.myNullability &&
           myInstanceofValues.equals(aState.myInstanceofValues) &&
           myNotInstanceofValues.equals(aState.myNotInstanceofValues);
  }

  protected DfaVariableState createCopy(Set<DfaPsiType> instanceofValues, Set<DfaPsiType> notInstanceofValues, Nullness nullability) {
    return new DfaVariableState(instanceofValues, notInstanceofValues, nullability);
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
    return buf.toString();
  }

  public Nullness getNullability() {
    return myNullability;
  }

  public boolean isNotNull() {
    return myNullability == Nullness.NOT_NULL;
  }

  DfaVariableState withNullability(@NotNull Nullness nullness) {
    return myNullability == nullness ? this : createCopy(myInstanceofValues, myNotInstanceofValues, nullness);
  }

  public DfaVariableState withNullable(final boolean nullable) {
    return myNullability != Nullness.NOT_NULL ? withNullability(nullable ? Nullness.NULLABLE : Nullness.UNKNOWN) : this;
  }

  public DfaVariableState withValue(DfaValue value) {
    return this;
  }

  @Nullable
  public DfaValue getValue() {
    return null;
  }

  public Set<DfaPsiType> getInstanceofValues() {
    return myInstanceofValues;
  }

  public Set<DfaPsiType> getNotInstanceofValues() {
    return myNotInstanceofValues;
  }

}
