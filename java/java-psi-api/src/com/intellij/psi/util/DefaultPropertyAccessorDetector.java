// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultPropertyAccessorDetector implements PropertyAccessorDetector {
  private static final ExtensionPointName<PropertyAccessorDetector> EP_NAME = ExtensionPointName.create("com.intellij.propertyAccessorDetector");

  public static @Nullable PropertyAccessorInfo detectFrom(@NotNull PsiMethod method) {
    for (PropertyAccessorDetector detector : EP_NAME.getExtensions()) {
      PropertyAccessorInfo accessorInfo = detector.detectPropertyAccessor(method);
      if (accessorInfo != null) {
        return accessorInfo;
      }
    }
    return getAccessorInfo(method);
  }

  @Nullable
  private static PropertyAccessorInfo getAccessorInfo(@NotNull PsiMethod method) {
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

  @Override
  public @Nullable PropertyAccessorInfo detectPropertyAccessor(@NotNull PsiMethod method) {
    return getAccessorInfo(method);
  }
}
