// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class FindBugsAnnotationSupport implements AnnotationPackageSupport {
  @NotNull
  @Override
  public List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Collections.singletonList("edu.umd.cs.findbugs.annotations.NonNull");
      case NULLABLE -> Collections.singletonList("edu.umd.cs.findbugs.annotations.Nullable");
      default -> Collections.emptyList();
    };
  }
}
