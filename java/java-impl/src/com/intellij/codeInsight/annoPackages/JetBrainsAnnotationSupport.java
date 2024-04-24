// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

final class JetBrainsAnnotationSupport implements AnnotationPackageSupport {
  @Override
  public @NotNull List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Collections.singletonList(AnnotationUtil.NOT_NULL);
      case NULLABLE -> Collections.singletonList(AnnotationUtil.NULLABLE);
      case UNKNOWN -> Collections.singletonList(AnnotationUtil.UNKNOWN_NULLABILITY);
    };
  }

  @Override
  public boolean isTypeUseAnnotationLocationRestricted() {
    return true;
  }
}
