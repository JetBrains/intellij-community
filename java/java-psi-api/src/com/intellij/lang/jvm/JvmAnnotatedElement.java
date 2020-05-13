// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JvmAnnotatedElement extends JvmElement {

  JvmAnnotation @NotNull [] getAnnotations();

  @Nullable
  default JvmAnnotation getAnnotation(@NotNull @NonNls String fqn) {
    return JvmAnnotatedElementDefaults.getAnnotation(this, fqn);
  }

  default boolean hasAnnotation(@NotNull @NonNls String fqn) {
    return getAnnotation(fqn) != null;
  }
}
