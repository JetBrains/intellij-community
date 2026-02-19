// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class DfNullConstantType extends DfConstantType<Object> implements DfReferenceType {
  DfNullConstantType() {
    super(null);
  }

  @Override
  public @NotNull DfaNullability getNullability() {
    return DfaNullability.NULL;
  }

  @Override
  public @NotNull TypeConstraint getConstraint() {
    return TypeConstraints.TOP;
  }

  @Override
  public DfType tryNegate() {
    return DfTypes.NOT_NULL_OBJECT;
  }

  @Override
  public @NotNull DfReferenceType dropNullability() {
    return DfTypes.OBJECT_OR_NULL;
  }

  @Override
  public @NotNull DfReferenceType convert(TypeConstraints.@NotNull TypeConstraintFactory factory) {
    return this;
  }

  @Override
  public @NotNull DfType join(@NotNull DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (!(other instanceof DfReferenceType type)) return TOP;
    Set<Object> notValues = type instanceof DfGenericObjectType objectType ? objectType.getRawNotValues() : Set.of();
    return new DfGenericObjectType(notValues, type.getConstraint(), DfaNullability.NULL.unite(type.getNullability()),
                                   type.getMutability(), type.getSpecialField(), type.getSpecialFieldType(), false);
  }

  @Override
  public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    if (isMergeable(other)) return this;
    if (other.isMergeable(this)) return other;
    if (other instanceof DfGenericObjectType) return other.tryJoinExactly(this);
    return null;
  }
}
