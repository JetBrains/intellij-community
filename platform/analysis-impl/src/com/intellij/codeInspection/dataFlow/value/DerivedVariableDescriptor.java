// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.types.DfType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return dfType.getDerivedValues().getOrDefault(this, DfType.TOP);
  }

  /**
   * Returns a DfaValue which represents this special field
   *
   * @param factory a factory to create new values if necessary
   * @param qualifier a known qualifier value
   * @return a DfaValue which represents this special field
   */
  @Override
  @NotNull
  default DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (qualifier == null) {
      return factory.fromDfType(getDefaultValue());
    }
    if (qualifier instanceof DfaWrappedValue wrappedValue && wrappedValue.getSpecialField() == this) {
      return wrappedValue.getWrappedValue();
    }
    if (qualifier instanceof DfaVariableValue) {
      return factory.getVarFactory().createVariableValue(this, (DfaVariableValue)qualifier);
    }
    return factory.fromDfType(getFromQualifier(qualifier.getDfType()).meet(getDefaultValue()));
  }

  /**
   * Returns a dfType that describes any possible value this derived variable may have, 
   * in the absence of any additional information.
   *
   * @return a dfType for the default value
   */
  default DfType getDefaultValue() {
    return DfType.TOP;
  }

  /**
   * @return true if the equality of this derived variable implies the equality of qualifiers.
   * Could be useful for boxed values processing.
   */
  default boolean equalityImpliesQualifierEquality() {
    return false;
  }
}
