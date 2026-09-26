// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DefaultPropertyAccessorDetector {

  static @Nullable PropertyAccessorDetector.PropertyAccessorInfo getDefaultAccessorInfo(@NotNull PsiMethod method) {
    if (PropertyUtilBase.isSimplePropertyGetter(method)) {
      return new PropertyAccessorDetector.PropertyAccessorInfo(PropertyUtilBase.getPropertyNameByGetter(method),
                                                               method.getReturnType(),
                                                               PropertyKind.GETTER);
    }
    else if (PropertyUtilBase.isSimplePropertySetter(method)) {
      return new PropertyAccessorDetector.PropertyAccessorInfo(PropertyUtilBase.getPropertyNameBySetter(method),
                                                               method.getParameterList().getParameters()[0].getType(),
                                                               PropertyKind.SETTER);
    }
    return null;
  }
}
