// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PropertyAccessorDetector {
  ExtensionPointName<PropertyAccessorDetector> EP_NAME = ExtensionPointName.create("com.intellij.propertyAccessorDetector");

  static @Nullable PropertyAccessorInfo detectFrom(@NotNull PsiMethod method) {
    for (PropertyAccessorDetector detector : EP_NAME.getExtensions()) {
      PropertyAccessorInfo accessorInfo = detector.detectPropertyAccessor(method);
      if (accessorInfo != null) {
        return accessorInfo;
      }
    }
    return getDefaultAccessorInfo(method);
  }

  @Nullable
  static PropertyAccessorInfo getDefaultAccessorInfo(@NotNull PsiMethod method) {
    if (PropertyUtilBase.isSimplePropertyGetter(method)) {
      return new PropertyAccessorInfo(PropertyUtilBase.getPropertyNameByGetter(method),
                                      method.getReturnType(),
                                      PropertyKind.GETTER);
    }
    else if (PropertyUtilBase.isSimplePropertySetter(method)) {
      return new PropertyAccessorInfo(PropertyUtilBase.getPropertyNameBySetter(method),
                                      method.getParameterList().getParameters()[0].getType(),
                                      PropertyKind.SETTER);
    }
    return null;
  }

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