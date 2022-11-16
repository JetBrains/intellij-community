// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Type that corresponds to JVM reference type; represents subset of possible reference values (may include null)
 */
public interface DfReferenceType extends DfType {
  /**
   * @return nullability of this type
   */
  @NotNull
  DfaNullability getNullability();

  /**
   * @return type constraint of this type
   */
  @NotNull
  TypeConstraint getConstraint();

  /**
   * @return mutability of all the objects referred by this type
   */
  @NotNull
  default Mutability getMutability() {
    return Mutability.UNKNOWN;
  }

  /**
   * @return special field if additional information is known about the special field of all referenced objects
   */
  @Nullable
  default SpecialField getSpecialField() {
    return null;
  }

  /**
   * @return type of special field; {@link DfType#BOTTOM} if {@link #getSpecialField()} returns null
   */
  @NotNull
  default DfType getSpecialFieldType() {
    return BOTTOM;
  }

  /**
   * @return this type without type constraint, or simply this type if it's a constant
   */
  @NotNull
  default DfReferenceType dropTypeConstraint() {
    return this;
  }

  /**
   * @return this type without locality flag, or simply this type if it's a constant
   */
  @NotNull
  default DfReferenceType dropLocality() {
    return this;
  }

  /**
   * @return this type without nullability knowledge, or simply this type if it's a constant
   */
  @NotNull
  DfReferenceType dropNullability();

  /**
   * @return this type without mutability knowledge, or simply this type if it's a constant
   */
  @NotNull
  default DfReferenceType dropMutability() {
    return this;
  }

  /**
   * @return this type without special field knowledge, or simply this type if it's a constant
   */
  default @NotNull DfReferenceType dropSpecialField() {
    return this;
  }

  @Override
  default DfType correctTypeOnFlush(DfType typeBeforeFlush) {
    DfaNullability inherentNullability = this.getNullability();
    if (inherentNullability == DfaNullability.NULLABLE) {
      DfaNullability nullability = DfaNullability.fromDfType(typeBeforeFlush);
      if (nullability == DfaNullability.FLUSHED || nullability == DfaNullability.NULL || nullability == DfaNullability.NOT_NULL) {
        return dropNullability().meet(DfaNullability.FLUSHED.asDfType());
      }
    }
    return this;
  }

  @Override
  default DfType correctForClosure() {
    DfReferenceType newType = dropLocality();
    if (newType.getMutability() == Mutability.MUST_NOT_MODIFY) {
      // If we must not modify parameter inside the method body itself,
      // we may still be able to modify it inside nested closures,
      // as they could be executed later.
      newType = newType.dropMutability();
    }
    return newType;
  }

  @Override
  @NotNull
  default DfType getBasicType() {
    return dropSpecialField();
  }

  @Override
  default @NotNull List<@NotNull DerivedVariableDescriptor> getDerivedVariables() {
    SpecialField field = SpecialField.fromQualifierType(this);
    if (field != null) {
      return List.of(field);
    }
    return List.of();
  }

  @Override
  @NotNull
  default DfType getDerivedValue(@NotNull DerivedVariableDescriptor derivedDescriptor) {
    if (getSpecialField() == derivedDescriptor) {
      return getSpecialFieldType();
    }
    return DfType.TOP;
  }

  @Override
  default boolean mayAlias(DfType otherType) {
    if (isLocal() || otherType.isLocal()) return false;
    if (otherType.meet(this) != BOTTOM) {
      // possible aliasing
      return true;
    }
    if (SpecialField.fromQualifierType(otherType) == SpecialField.COLLECTION_SIZE &&
        SpecialField.fromQualifierType(this) == SpecialField.COLLECTION_SIZE) {
      // Collection size is sometimes derived from another (incompatible) collection
      // e.g. keySet Set size is affected by Map size.
      return true;
    }
    return false;
  }

  @Override
  default boolean isImmutableQualifier() {
    return getMutability() == Mutability.UNMODIFIABLE;
  }

  @Override
  @NlsSafe @NotNull String toString();
}
