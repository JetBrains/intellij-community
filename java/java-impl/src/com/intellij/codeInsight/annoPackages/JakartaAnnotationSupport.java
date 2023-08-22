// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

// packages: jakarta.annotation:jakarta.annotation-api
class JakartaAnnotationSupport implements AnnotationPackageSupport {
  @NotNull
  @Override
  public List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> List.of("jakarta.annotation.Nonnull");
      case NULLABLE -> List.of("jakarta.annotation.Nullable");
      default -> Collections.emptyList();
    };
  }
}
