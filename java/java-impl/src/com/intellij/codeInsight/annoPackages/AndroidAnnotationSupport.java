// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class AndroidAnnotationSupport implements AnnotationPackageSupport {
  @Override
  public @NotNull List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Arrays.asList("android.annotation.NonNull",
                                     "androidx.annotation.NonNull",
                                     "com.android.annotations.NonNull",
                                     "android.support.annotation.NonNull",
                                     "androidx.annotation.RecentlyNonNull");
      case NULLABLE -> Arrays.asList("android.annotation.Nullable",
                                     "androidx.annotation.Nullable",
                                     "com.android.annotations.Nullable",
                                     "android.support.annotation.Nullable",
                                     "androidx.annotation.RecentlyNullable");
      default -> Collections.emptyList();
    };
  }
}
