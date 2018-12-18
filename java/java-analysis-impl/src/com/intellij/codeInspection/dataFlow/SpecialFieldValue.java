// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaFactMapValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A special field with associated information about its value
 */
public final class SpecialFieldValue {
  private final @NotNull SpecialField myField;
  private final @NotNull DfaValue myValue;

  public SpecialFieldValue(@NotNull SpecialField field, @NotNull DfaValue value) {
    if (value instanceof DfaFactMapValue) {
      myValue = ((DfaFactMapValue)value).withFact(DfaFactType.SPECIAL_FIELD_VALUE, null);
    }
    else if (value instanceof DfaConstValue) {
      myValue = value;
    }
    else {
      throw new IllegalArgumentException("Unexpected value: " + value);
    }
    myField = field;
  }

  @NotNull
  public SpecialField getField() {
    return myField;
  }

  @NotNull
  public DfaValue getValue() {
    return myValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SpecialFieldValue)) return false;
    SpecialFieldValue value = (SpecialFieldValue)o;
    return myField == value.myField && myValue == value.myValue;
  }

  @Override
  public int hashCode() {
    return myField.hashCode() * 31 + Objects.hashCode(myValue);
  }

  @Override
  public String toString() {
    return myField + " = " + myValue;
  }

  public String getPresentationText(PsiType type) {
    return myField.getPresentationText(myValue, type);
  }
}
