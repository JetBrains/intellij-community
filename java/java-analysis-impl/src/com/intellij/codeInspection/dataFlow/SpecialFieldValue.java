// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A special field with associated information about its value
 * @deprecated could be removed in future; used only in DfaFactMap and TrackingRunner
 */
@Deprecated
final class SpecialFieldValue {
  private final @NotNull SpecialField myField;
  private final @NotNull DfType myType;

  SpecialFieldValue(@NotNull SpecialField field, @NotNull DfType type) {
    myType = type instanceof DfReferenceType ? ((DfReferenceType)type).dropSpecialField() : type;
    myField = field;
  }

  @NotNull
  public SpecialField getField() {
    return myField;
  }

  @NotNull
  public DfType getDfType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SpecialFieldValue)) return false;
    SpecialFieldValue value = (SpecialFieldValue)o;
    return myField == value.myField && myType == value.myType;
  }

  @Nullable
  public SpecialFieldValue unite(SpecialFieldValue other) {
    if (other == this) return this;
    if (myField != other.myField) return null;
    DfType type = myType.join(other.myType);
    return type == DfTypes.TOP ? null : new SpecialFieldValue(myField, type);
  }
  
  @Override
  public int hashCode() {
    return myField.hashCode() * 31 + Objects.hashCode(myType);
  }

  @Override
  public String toString() {
    return myField + " = " + myType;
  }
}
