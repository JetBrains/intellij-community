// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class JvmAnnotatedElementDefaults {

  @Nullable
  static JvmAnnotation getAnnotation(@NotNull JvmAnnotatedElement element, @NotNull String fqn) {
    for (JvmAnnotation annotation : element.getAnnotations()) {
      if (fqn.equals(annotation.getQualifiedName())) return annotation;
    }
    return null;
  }
}
