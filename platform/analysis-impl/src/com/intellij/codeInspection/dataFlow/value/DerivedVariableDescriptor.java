// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * A variable descriptor that could be used as a part of composite DfType 
 * (assuming that its qualifier is a corresponding basic type)
 */
public interface DerivedVariableDescriptor extends VariableDescriptor {
  /**
   * @param fieldValue dfType of the special field value
   * @return a dfType that represents a value having this special field restricted to the supplied dfType
   */
  @NotNull DfType asDfType(@NotNull DfType fieldValue);

  /**
   * @param qualifierType type of the qualifier
   * @param fieldValue dfType of the special field value
   * @return a dfType that represents a value having this special field restricted to the supplied dfType.
   * Unlike {@link #asDfType(DfType)} this overload may canonicalize some values.
   */
  @NotNull DfType asDfType(@NotNull DfType qualifierType, @NotNull DfType fieldValue);

  /**
   * Returns a DfType from given DfType qualifier if it's bound to this special field
   * @param dfType of the qualifier
   * @return en extracted DfType
   */
  @NotNull
  default DfType getFromQualifier(@NotNull DfType dfType) {
    return dfType.getDerivedValue(this);
  }

  /**
   * @return true if the equality of this derived variable implies the equality of qualifiers.
   * Could be useful for boxed values processing.
   */
  default boolean equalityImpliesQualifierEquality() {
    return false;
  }
}
