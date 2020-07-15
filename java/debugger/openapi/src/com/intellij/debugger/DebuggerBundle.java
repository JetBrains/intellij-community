// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link JavaDebuggerBundle} instead
 */
@Deprecated
public final class DebuggerBundle {
  @NotNull
  public static String message(@NotNull String key, Object @NotNull ... params) {
    return JavaDebuggerBundle.message(key, params);
  }
}
