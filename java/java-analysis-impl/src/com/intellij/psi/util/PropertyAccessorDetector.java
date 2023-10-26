// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    for (PropertyAccessorDetector detector : EP_NAME.getExtensionList()) {
      PropertyAccessorInfo accessorInfo = detector.detectPropertyAccessor(method);
      if (accessorInfo != null) {
        return accessorInfo;
      }
    }
    return DefaultPropertyAccessorDetector.getDefaultAccessorInfo(method);
  }

  /**
   * Detects property access information if any, or results to null
   */
  @Nullable PropertyAccessorInfo detectPropertyAccessor(@NotNull PsiMethod method);

  record PropertyAccessorInfo(@NotNull String propertyName, @NotNull PsiType propertyType, @NotNull PropertyKind kind) {
    public boolean isKindOf(PropertyKind other) {
        return this.kind == other;
      }
    }
}