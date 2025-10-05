// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a type that contains exactly one value
 */
public abstract class DfConstantType<T> implements DfType {
  private final T myValue;

  protected DfConstantType(T value) {
    myValue = value;
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    return (other instanceof DfConstantType<?> constant && Objects.equals(myValue, constant.myValue)) || 
           other == DfType.BOTTOM;
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    return other.isSuperType(this) ? this : DfType.BOTTOM;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    if (relationType == RelationType.EQ || relationType == RelationType.IS) return this;
    if (relationType == RelationType.NE || relationType == RelationType.IS_NOT) {
      DfType negated = tryNegate();
      if (negated != null) {
        return negated;
      }
    }
    return TOP;
  }

  /**
   * @return a value representation (different constants have different representation)
   */
  public T getValue() {
    return myValue;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myValue);
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
           obj != null && obj.getClass() == getClass() && Objects.equals(((DfConstantType<?>)obj).myValue, myValue);
  }

  @Override
  public @NotNull String toString() {
    return String.valueOf(myValue);
  }

  @Override
  public boolean isConst(@Nullable Object constant) {
    return Objects.equals(myValue, constant);
  }

  @Override
  public <C> @Nullable C getConstantOfType(@NotNull Class<C> clazz) {
    return ObjectUtils.tryCast(myValue, clazz);
  }
}
