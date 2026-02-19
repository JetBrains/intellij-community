// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link JavaDebuggerBundle} instead
 */
@Deprecated
public final class DebuggerBundle {
  public static @NotNull String message(@NotNull String key, Object @NotNull ... params) {
    return JavaDebuggerBundle.message(key, params);
  }
}
