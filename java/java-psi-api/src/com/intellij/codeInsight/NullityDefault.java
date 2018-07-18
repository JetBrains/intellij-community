// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.PsiAnnotation;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
class NullityDefault {
  @NotNull final PsiAnnotation annotation;
  final boolean isNullableDefault;

  NullityDefault(@NotNull PsiAnnotation annotation, boolean isNullableDefault) {
    this.annotation = annotation;
    this.isNullableDefault = isNullableDefault;
  }
}
