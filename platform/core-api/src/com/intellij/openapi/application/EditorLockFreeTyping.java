// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class EditorLockFreeTyping {
  private static final boolean IS_ENABLED = Registry.is("editor.lockfree.typing.enabled", false);

  public static boolean isEnabled() {
    return IS_ENABLED;
  }
}
