// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link JavaCompilerBundle} instead
 */
@Deprecated(forRemoval = true)
public final class CompilerBundle {
  public static @NotNull String message(@NotNull String key, Object @NotNull ... params) {
    return JavaCompilerBundle.message(key, params);
  }
}
