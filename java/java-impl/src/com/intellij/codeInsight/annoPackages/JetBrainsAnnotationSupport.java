// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class JetBrainsAnnotationSupport implements AnnotationPackageSupport {
  @NotNull
  @Override
  public List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    switch (nullability) {
      case NOT_NULL:
        return Collections.singletonList(AnnotationUtil.NOT_NULL);
      case NULLABLE:
        return Collections.singletonList(AnnotationUtil.NULLABLE);
      default:
        return Collections.emptyList();
    }
  }
}
