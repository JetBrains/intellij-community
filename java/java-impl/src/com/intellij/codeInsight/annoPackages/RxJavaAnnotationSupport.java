// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class RxJavaAnnotationSupport implements AnnotationPackageSupport {
  private final static String NOTNULL_RXJAVA_2 = "io.reactivex.annotations.NonNull";
  private final static String NULLABLE_RXJAVA_2 = "io.reactivex.annotations.Nullable";
  private final static String NOTNULL_RXJAVA_3 = "io.reactivex.rxjava3.annotations.NonNull";
  private final static String NULLABLE_RXJAVA_3 = "io.reactivex.rxjava3.annotations.Nullable";

  private static final List<String> NOT_NULLS = ContainerUtil.immutableList(NOTNULL_RXJAVA_2, NOTNULL_RXJAVA_3);
  private static final List<String> NULLABLES = ContainerUtil.immutableList(NULLABLE_RXJAVA_2, NULLABLE_RXJAVA_3);

  @Override
  public @NotNull List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    switch (nullability) {
      case NOT_NULL:
        return NOT_NULLS;
      case NULLABLE:
        return NULLABLES;
      default:
        return Collections.emptyList();
    }
  }
}
