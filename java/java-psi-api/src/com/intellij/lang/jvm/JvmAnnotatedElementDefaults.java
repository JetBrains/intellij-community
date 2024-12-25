// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JvmAnnotatedElementDefaults {

  static @Nullable JvmAnnotation getAnnotation(@NotNull JvmAnnotatedElement element, @NotNull String fqn) {
    for (JvmAnnotation annotation : element.getAnnotations()) {
      if (fqn.equals(annotation.getQualifiedName())) return annotation;
    }
    return null;
  }
}
