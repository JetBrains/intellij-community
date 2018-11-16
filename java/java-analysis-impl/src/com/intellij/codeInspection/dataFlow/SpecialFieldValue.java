// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A special field with associated information about its value
 */
public final class SpecialFieldValue {
  private final @NotNull SpecialField myField;
  private final @Nullable Object myValue;
  private final @NotNull PsiType myType;
  
  public SpecialFieldValue(@NotNull SpecialField field, @Nullable Object value, @NotNull PsiType type) {
    myField = field;
    myValue = value;
    myType = type;
  }

  @NotNull
  public SpecialField getField() {
    return myField;
  }

  @Nullable
  public Object getValue() {
    return myValue;
  }

  @NotNull
  public PsiType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SpecialFieldValue)) return false;
    SpecialFieldValue value = (SpecialFieldValue)o;
    return myField == value.myField && myType.equals(value.myType) && Objects.equals(myValue, value.myValue);
  }

  @Override
  public int hashCode() {
    return myField.hashCode() * 31 + Objects.hashCode(myValue);
  }

  @Override
  public String toString() {
    return myField + " = " + myValue;
  }

  public DfaConstValue toConstant(DfaValueFactory factory) {
    return factory.getConstFactory().createFromValue(myValue, myType);
  }
}
