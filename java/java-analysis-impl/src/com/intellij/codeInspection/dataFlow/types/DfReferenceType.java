// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.jvm.JvmSpecialField;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
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
   * @return true if this type contains references only to local objects (not leaked from the current context to unknown methods)
   */
  default boolean isLocal() {
    return false;
  }

  /**
   * @return special field if additional information is known about the special field of all referenced objects
   */
  @Nullable
  default JvmSpecialField getSpecialField() {
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
  default DfReferenceType dropSpecialField() {
    return this;
  }

  /**
   * @param type type to check
   * @return true if the supplied type is a reference type that contains references only
   * to local objects (not leaked from the current context to unknown methods)
   */
  static boolean isLocal(DfType type) {
    return type instanceof DfReferenceType && ((DfReferenceType)type).isLocal();
  }

  /**
   * @param type type to drop
   * @return this type dropping any relation to the supplied type
   */
  @NotNull
  default DfType withoutType(@NotNull TypeConstraint type) {
    TypeConstraint constraint = getConstraint();
    if (constraint.equals(type)) {
      return dropTypeConstraint();
    }
    if (type instanceof TypeConstraint.Exact) {
      TypeConstraint.Exact exact = (TypeConstraint.Exact)type;
      TypeConstraint result = TypeConstraints.TOP;
      result = constraint.instanceOfTypes().without(exact).map(TypeConstraint.Exact::instanceOf)
        .foldLeft(result, TypeConstraint::meet);
      result = constraint.notInstanceOfTypes().without(exact).map(TypeConstraint.Exact::notInstanceOf)
        .foldLeft(result, TypeConstraint::meet);
      return dropTypeConstraint().meet(result.asDfType());
    }
    return this;
  }

  @Override
  default boolean containsConstant(@NotNull DfConstantType<?> constant) {
    DfReferenceType filtered = dropTypeConstraint();
    if (getConstraint().isComparedByEquals()) {
      filtered = filtered.dropLocality();
    }
    return filtered.isSuperType(constant);
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
  default @NotNull List<@NotNull VariableDescriptor> getDerivedVariables() {
    JvmSpecialField field = JvmSpecialField.fromQualifierType(this);
    if (field != null) {
      return List.of(field);
    }
    return List.of();
  }

  @Override
  @NotNull
  default DfType getDerivedValue(@NotNull VariableDescriptor derivedDescriptor) {
    if (getSpecialField() == derivedDescriptor) {
      return getSpecialFieldType();
    }
    return DfType.TOP;
  }

  @Override
  @NlsSafe @NotNull String toString();
}
