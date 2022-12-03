// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PropertyAccessorDetector {
  /**
   * Detects property access information if any, or results to null
   */
  @Nullable PropertyAccessorInfo detectPropertyAccessor(@NotNull PsiMethod method);

  class PropertyAccessorInfo {
    private final @NotNull String propertyName;
    private final @NotNull PsiType propertyType;
    private final @NotNull PropertyKind kind;

    public PropertyAccessorInfo(@NotNull String propertyName, @NotNull PsiType propertyType, @NotNull PropertyKind kind) {
      this.propertyName = propertyName;
      this.propertyType = propertyType;
      this.kind = kind;
    }

    public @NotNull String getPropertyName() {
      return propertyName;
    }

    public @NotNull PsiType getPropertyType() {
      return propertyType;
    }

    public @NotNull PropertyKind getKind() {
      return kind;
    }

    public boolean isKindOf(PropertyKind other) {
      return this.kind == other;
    }
  }
}