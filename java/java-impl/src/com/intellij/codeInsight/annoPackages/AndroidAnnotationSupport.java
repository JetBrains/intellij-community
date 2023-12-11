// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class AndroidAnnotationSupport implements AnnotationPackageSupport {
  @NotNull
  @Override
  public List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Arrays.asList("android.support.annotation.NonNull",
                                     "androidx.annotation.NonNull",
                                     "androidx.annotation.RecentlyNonNull",
                                     "com.android.annotations.NonNull");
      case NULLABLE -> Arrays.asList("android.support.annotation.Nullable",
                                     "androidx.annotation.Nullable",
                                     "androidx.annotation.RecentlyNullable",
                                     "com.android.annotations.Nullable");
      default -> Collections.emptyList();
    };
  }
}
