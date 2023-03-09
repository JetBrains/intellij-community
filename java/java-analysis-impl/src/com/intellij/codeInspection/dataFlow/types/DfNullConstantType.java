// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public DfaNullability getNullability() {
    return DfaNullability.NULL;
  }

  @NotNull
  @Override
  public TypeConstraint getConstraint() {
    return TypeConstraints.TOP;
  }

  @Override
  public DfType tryNegate() {
    return DfTypes.NOT_NULL_OBJECT;
  }

  @NotNull
  @Override
  public DfReferenceType dropNullability() {
    return DfTypes.OBJECT_OR_NULL;
  }

  @Override
  public @NotNull DfReferenceType convert(TypeConstraints.@NotNull TypeConstraintFactory factory) {
    return this;
  }

  @NotNull
  @Override
  public DfType join(@NotNull DfType other) {
    if (isSuperType(other)) return this;
    if (other.isSuperType(this)) return other;
    if (!(other instanceof DfReferenceType)) return TOP;
    DfReferenceType type = (DfReferenceType)other;
    Set<Object> notValues = type instanceof DfGenericObjectType ? ((DfGenericObjectType)type).getRawNotValues() : Set.of();
    return new DfGenericObjectType(notValues, type.getConstraint(), DfaNullability.NULL.unite(type.getNullability()),
                                   type.getMutability(), null, BOTTOM, false);
  }

  @Override
  public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    if (isMergeable(other)) return this;
    if (other.isMergeable(this)) return other;
    if (other instanceof DfGenericObjectType) return other.tryJoinExactly(this);
    return null;
  }
}
