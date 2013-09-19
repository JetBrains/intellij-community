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

import java.util.Set;

public class DfaVariableState implements Cloneable {
  private final Set<DfaPsiType> myInstanceofValues;
  private final Set<DfaPsiType> myNotInstanceofValues;
  private Nullness myNullability;

  public DfaVariableState(@NotNull DfaVariableValue dfaVar) {
    myInstanceofValues = ContainerUtil.newTroveSet();
    myNotInstanceofValues = ContainerUtil.newTroveSet();

    myNullability = dfaVar.getInherentNullability();
    DfaTypeValue initialType = dfaVar.getTypeValue();
    if (initialType != null) {
      setInstanceofValue(initialType);
    }
  }

  protected DfaVariableState(final DfaVariableState toClone) {
    myInstanceofValues = ContainerUtil.newTroveSet(toClone.myInstanceofValues);
    myNotInstanceofValues = ContainerUtil.newTroveSet(toClone.myNotInstanceofValues);
    myNullability = toClone.myNullability;
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

  public boolean setInstanceofValue(DfaTypeValue dfaType) {
    if (dfaType.isNullable()) {
      myNullability = Nullness.NULLABLE;
    }

    if (dfaType.getDfaType().getPsiType() instanceof PsiPrimitiveType) return true;

    if (checkInstanceofValue(dfaType.getDfaType())) {
      myInstanceofValues.add(dfaType.getDfaType());
      return true;
    }

    return false;
  }

  public boolean addNotInstanceofValue(DfaTypeValue dfaType) {
    if (myNotInstanceofValues.contains(dfaType.getDfaType())) return true;

    for (DfaPsiType dfaTypeValue : myInstanceofValues) {
      if (dfaType.getDfaType().isAssignableFrom(dfaTypeValue)) return false;
    }

    myNotInstanceofValues.add(dfaType.getDfaType());
    return true;
  }

  public int hashCode() {
    return myInstanceofValues.hashCode() + myNotInstanceofValues.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaVariableState)) return false;
    DfaVariableState aState = (DfaVariableState) obj;
    return myInstanceofValues.equals(aState.myInstanceofValues) &&
           myNotInstanceofValues.equals(aState.myNotInstanceofValues) &&
           myNullability == aState.myNullability;
  }

  @Override
  protected DfaVariableState clone() {
    return new DfaVariableState(this);
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

  public boolean isNotNull() {
    return myNullability == Nullness.NOT_NULL;
  }

  public void setNullable(final boolean nullable) {
    if (myNullability != Nullness.NOT_NULL) {
      myNullability = nullable ? Nullness.NULLABLE : Nullness.UNKNOWN;
    }
  }

  public void setValue(DfaValue value) {
  }

  @Nullable
  public DfaValue getValue() {
    return null;
  }
}
